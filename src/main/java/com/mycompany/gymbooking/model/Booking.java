package com.mycompany.gymbooking.model;

import com.mycompany.gymbooking.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A session request from a member to a trainer. Status changes only go through the transition
 * methods, which enforce the booking rules.
 */
@Entity
@Table(name = "bookings", indexes = {
        // Queried on every availability check
        @Index(name = "idx_bookings_trainer_date", columnList = "trainer_id, session_date"),
        @Index(name = "idx_bookings_member_date", columnList = "member_id, session_date")
})
public class Booking {

    public static final Set<BookingStatus> SLOT_HOLDING =
            EnumSet.of(BookingStatus.REQUESTED, BookingStatus.ACCEPTED, BookingStatus.PAID);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trainer_id")
    private Trainer trainer;

    /** Copied from the trainer at booking time so a later branch transfer doesn't affect past bookings. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "session_date", nullable = false)   // "date" is a reserved word in SQL
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private int durationMinutes;

    /** Trainer's rate at request time, so later rate changes don't affect this booking. */
    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal price;

    /** Stored as VARCHAR so adding a status doesn't require altering a MySQL ENUM column. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(length = 300)
    private String memberNote;

    @Column(length = 300)
    private String trainerReply;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Trainer's answer deadline; an unanswered request expires after it. */
    @Column(nullable = false)
    private LocalDateTime respondByAt;

    private LocalDateTime respondedAt;

    /** Payment deadline for an accepted booking; an unpaid booking expires after it. */
    private LocalDateTime payByAt;

    /** Last moment a paid booking can be cancelled with a refund. */
    private LocalDateTime refundableUntil;

    private LocalDateTime cancelledAt;

    @Version
    private Long version;

    protected Booking() {
    }

    public Booking(Member member, Trainer trainer, LocalDate date, LocalTime startTime, int durationMinutes,
                   BigDecimal price, String memberNote, LocalDateTime now, LocalDateTime respondByAt) {
        this.member = member;
        this.trainer = trainer;
        this.branch = trainer.getBranch();
        this.date = date;
        this.startTime = startTime;
        this.endTime = startTime.plusMinutes(durationMinutes);
        this.durationMinutes = durationMinutes;
        this.price = price;
        this.memberNote = clean(memberNote);
        this.status = BookingStatus.REQUESTED;
        this.createdAt = now;
        this.respondByAt = respondByAt;
    }

    /**
     * Effective status at {@code now}. Overdue bookings are reported as EXPIRED before
     * BookingExpiryJob persists the change.
     */
    public BookingStatus statusAt(LocalDateTime now) {
        if (status == BookingStatus.REQUESTED && !now.isBefore(respondByAt)) {
            return BookingStatus.EXPIRED;
        }
        if (status == BookingStatus.ACCEPTED && !now.isBefore(payDeadline())) {
            return BookingStatus.EXPIRED;
        }
        return status;
    }

    /** Payment deadline. Bookings accepted before payByAt existed can be paid until the session starts. */
    public LocalDateTime payDeadline() {
        return payByAt != null ? payByAt : getStartsAt();
    }

    public boolean holdsSlotAt(LocalDateTime now) {
        return SLOT_HOLDING.contains(statusAt(now));
    }

    /** True if the half-open range [otherStart, otherEnd) overlaps this booking. */
    public boolean overlaps(LocalTime otherStart, LocalTime otherEnd) {
        return startTime.isBefore(otherEnd) && otherStart.isBefore(endTime);
    }

    public LocalDateTime getStartsAt() {
        return LocalDateTime.of(date, startTime);
    }

    public LocalDateTime getEndsAt() {
        return LocalDateTime.of(date, endTime);
    }

    public boolean canBePaidAt(LocalDateTime now) {
        return statusAt(now) == BookingStatus.ACCEPTED;   // past the pay deadline statusAt() returns EXPIRED
    }

    /**
     * Last moment the member can cancel (the session start, or refundableUntil once paid),
     * or null if the booking can't be cancelled.
     */
    public LocalDateTime cancelDeadline(LocalDateTime now) {
        return switch (statusAt(now)) {
            case REQUESTED, ACCEPTED -> getStartsAt();
            case PAID -> refundableUntil;
            default -> null;
        };
    }

    public boolean canBeCancelledAt(LocalDateTime now) {
        LocalDateTime deadline = cancelDeadline(now);
        return deadline != null && now.isBefore(deadline);
    }

    public void accept(String reply, LocalDateTime now, LocalDateTime payBy) {
        requirePending(now);
        this.status = BookingStatus.ACCEPTED;
        this.trainerReply = clean(reply);
        this.respondedAt = now;
        this.payByAt = payBy;
    }

    public void reject(String reason, LocalDateTime now) {
        requirePending(now);
        this.status = BookingStatus.REJECTED;
        this.trainerReply = clean(reason);
        this.respondedAt = now;
    }

    public void cancelByMember(LocalDateTime now) {
        BookingStatus current = statusAt(now);
        if (!SLOT_HOLDING.contains(current)) {
            throw new ConflictException("BOOKING_NOT_CANCELLABLE", "This booking is already " + label(current) + ".");
        }
        if (!now.isBefore(getStartsAt())) {
            throw new ConflictException("SESSION_STARTED", "This session has already started.");
        }
        if (current == BookingStatus.PAID && !now.isBefore(refundableUntil)) {
            long hours = Duration.between(refundableUntil, getStartsAt()).toHours();
            throw new ConflictException("TOO_LATE_TO_CANCEL",
                    "Paid sessions can only be cancelled until " + hours + " hours before they start.");
        }
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public void requirePayable(LocalDateTime now) {
        BookingStatus current = statusAt(now);
        switch (current) {
            case ACCEPTED -> { }
            case REQUESTED -> throw new ConflictException("NOT_ACCEPTED_YET", "The trainer hasn't accepted this request yet.");
            case PAID -> throw new ConflictException("ALREADY_PAID", "This session is already paid.");
            case EXPIRED -> throw new ConflictException("BOOKING_EXPIRED",
                    "This booking expired because it wasn't paid in time. Please book again.");
            default -> throw new ConflictException("BOOKING_NOT_PAYABLE", "This booking is " + label(current) + ".");
        }
    }

    public void markPaid(LocalDateTime now, LocalDateTime refundableUntil) {
        requirePayable(now);
        this.status = BookingStatus.PAID;
        this.refundableUntil = refundableUntil;
    }

    /** Marks an overdue request or unpaid booking as EXPIRED. Returns true if the status changed. */
    public boolean expireIfOverdue(LocalDateTime now) {
        boolean waiting = status == BookingStatus.REQUESTED || status == BookingStatus.ACCEPTED;
        if (waiting && statusAt(now) == BookingStatus.EXPIRED) {
            this.status = BookingStatus.EXPIRED;
            return true;
        }
        return false;
    }

    private void requirePending(LocalDateTime now) {
        BookingStatus current = statusAt(now);
        if (current == BookingStatus.EXPIRED) {
            throw new ConflictException("REQUEST_EXPIRED", "This request expired because it wasn't answered in time.");
        }
        if (current != BookingStatus.REQUESTED) {
            throw new ConflictException("BOOKING_NOT_PENDING", "This request was already " + label(current) + ".");
        }
    }

    private static String label(BookingStatus status) {
        return status.name().toLowerCase();
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public Trainer getTrainer() {
        return trainer;
    }

    public Branch getBranch() {
        return branch;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getMemberNote() {
        return memberNote;
    }

    public String getTrainerReply() {
        return trainerReply;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getRespondByAt() {
        return respondByAt;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public LocalDateTime getPayByAt() {
        return payByAt;
    }

    public LocalDateTime getRefundableUntil() {
        return refundableUntil;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }
}
