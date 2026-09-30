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
 * One session request / booking: a member, a trainer, a date and a time.
 *
 * ENCAPSULATION: there is no setStatus(). The status only changes through accept(), reject(),
 * markPaid(), cancelByMember() and expireIfOverdue(), and each of them checks the rules first.
 * So nobody can, for example, accept a request that was already cancelled, or pay for an expired one.
 */
@Entity
@Table(name = "bookings", indexes = {
        // Speeds up "which bookings does this trainer have on this day?" (asked for every availability check)
        @Index(name = "idx_bookings_trainer_date", columnList = "trainer_id, session_date"),
        @Index(name = "idx_bookings_member_date", columnList = "member_id, session_date")
})
public class Booking {

    /** These statuses keep the time slot taken, so nobody else can book it. */
    public static final Set<BookingStatus> SLOT_HOLDING =
            EnumSet.of(BookingStatus.REQUESTED, BookingStatus.ACCEPTED, BookingStatus.PAID);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Three RELATIONSHIPS → three foreign-key columns: member_id, trainer_id, branch_id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trainer_id")
    private Trainer trainer;

    /** Copied from the trainer when booked, so a later move to another branch doesn't change old bookings. */
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

    /** The price when the request was made (so changing the trainer's rate later doesn't change it). */
    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal price;

    /**
     * @JdbcTypeCode(VARCHAR): store it as plain text. Without it, Hibernate makes a MySQL
     * enum('ACCEPTED', ...) column, and a new status (like PAID) would be refused by MySQL.
     * (SchemaUpgrades changes the old enum column of existing databases.)
     */
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

    /** The trainer must answer before this moment, otherwise the request counts as EXPIRED. */
    @Column(nullable = false)
    private LocalDateTime respondByAt;

    private LocalDateTime respondedAt;

    /** ACCEPTED: the member must pay before this moment, otherwise the booking counts as EXPIRED. */
    private LocalDateTime payByAt;

    /** PAID: the last moment the member can still cancel (and get the money back). */
    private LocalDateTime refundableUntil;

    private LocalDateTime cancelledAt;

    /**
     * OPTIMISTIC LOCKING: Hibernate adds 1 to this number on every update, and only saves if the number
     * is still what it read. If the trainer accepts while the member cancels at the same second,
     * the second save fails instead of silently overwriting the first one.
     */
    @Version
    private Long version;

    /** Needed by JPA. */
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

    // ----------------------------------------------------------------
    // Questions about the booking
    // ----------------------------------------------------------------

    /**
     * The status as it really is at `now`.
     * A request nobody answered in time counts as EXPIRED right away, even before the
     * clean-up job (BookingExpiryJob) writes EXPIRED into the database.
     */
    public BookingStatus statusAt(LocalDateTime now) {
        if (status == BookingStatus.REQUESTED && !now.isBefore(respondByAt)) {
            return BookingStatus.EXPIRED;   // the trainer didn't answer in time
        }
        if (status == BookingStatus.ACCEPTED && !now.isBefore(payDeadline())) {
            return BookingStatus.EXPIRED;   // the member didn't pay in time
        }
        return status;
    }

    /**
     * When an ACCEPTED booking must be paid. Bookings accepted before Stage 4 have no payByAt:
     * they can be paid until the session starts.
     */
    public LocalDateTime payDeadline() {
        return payByAt != null ? payByAt : getStartsAt();
    }

    /** Does this booking still keep its time slot taken? */
    public boolean holdsSlotAt(LocalDateTime now) {
        return SLOT_HOLDING.contains(statusAt(now));
    }

    /** Do [start, end) and this booking's time share at least one minute? 10:00–11:00 and 10:30–11:30 → yes. */
    public boolean overlaps(LocalTime otherStart, LocalTime otherEnd) {
        return startTime.isBefore(otherEnd) && otherStart.isBefore(endTime);
    }

    public LocalDateTime getStartsAt() {
        return LocalDateTime.of(date, startTime);
    }

    public LocalDateTime getEndsAt() {
        return LocalDateTime.of(date, endTime);
    }

    /** The app shows a Pay button only when this is true. */
    public boolean canBePaidAt(LocalDateTime now) {
        return statusAt(now) == BookingStatus.ACCEPTED;   // ACCEPTED already means "before the pay deadline"
    }

    /**
     * The last moment the member can cancel, or null if it can't be cancelled at all.
     *   waiting / accepted (not paid) → until the session starts
     *   paid                          → until refundableUntil (24 h before the start)
     */
    public LocalDateTime cancelDeadline(LocalDateTime now) {
        return switch (statusAt(now)) {
            case REQUESTED, ACCEPTED -> getStartsAt();
            case PAID -> refundableUntil;
            default -> null;
        };
    }

    /** The app shows a Cancel button only when this is true. */
    public boolean canBeCancelledAt(LocalDateTime now) {
        LocalDateTime deadline = cancelDeadline(now);
        return deadline != null && now.isBefore(deadline);
    }

    // ----------------------------------------------------------------
    // Actions (each one checks the rules before changing anything)
    // ----------------------------------------------------------------

    /** @param payBy the member must pay before this moment (12 h from now, or the session start if sooner) */
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

    /** Throws a clear error if this booking can't be paid right now. */
    public void requirePayable(LocalDateTime now) {
        BookingStatus current = statusAt(now);
        switch (current) {
            case ACCEPTED -> { }   // OK
            case REQUESTED -> throw new ConflictException("NOT_ACCEPTED_YET", "The trainer hasn't accepted this request yet.");
            case PAID -> throw new ConflictException("ALREADY_PAID", "This session is already paid.");
            case EXPIRED -> throw new ConflictException("BOOKING_EXPIRED",
                    "This booking expired because it wasn't paid in time. Please book again.");
            default -> throw new ConflictException("BOOKING_NOT_PAYABLE", "This booking is " + label(current) + ".");
        }
    }

    /**
     * The money arrived: the session is confirmed.
     * @param refundableUntil the member may cancel (with a refund) until this moment
     */
    public void markPaid(LocalDateTime now, LocalDateTime refundableUntil) {
        requirePayable(now);
        this.status = BookingStatus.PAID;
        this.refundableUntil = refundableUntil;
    }

    /**
     * Used by the clean-up job. Returns true if this booking just became EXPIRED:
     * a request the trainer didn't answer in time, or an accepted booking that wasn't paid in time.
     */
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
        return status.name().toLowerCase();   // ACCEPTED → "accepted"
    }

    /** "  " → null, " hi " → "hi" */
    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    // ----------------------------------------------------------------
    // Getters (no setters on purpose)
    // ----------------------------------------------------------------

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
