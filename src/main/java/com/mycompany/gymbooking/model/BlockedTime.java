package com.mycompany.gymbooking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Time when a whole branch is closed or one trainer is off. Covers every day from startDate to
 * endDate, either all day or between startTime and endTime on each of those days.
 */
@Entity
@Table(name = "blocked_times", indexes = @Index(name = "idx_blocked_times_dates", columnList = "start_date, end_date"))
public class BlockedTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Exactly one of branch and trainer is set. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trainer_id")
    private Trainer trainer;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Both null for whole days. */
    private LocalTime startTime;

    private LocalTime endTime;

    @Column(length = 200)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected BlockedTime() {
    }

    private BlockedTime(Branch branch, Trainer trainer, LocalDate startDate, LocalDate endDate,
                        LocalTime startTime, LocalTime endTime, String reason, LocalDateTime now) {
        this.branch = branch;
        this.trainer = trainer;
        this.startDate = startDate;
        this.endDate = endDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason == null || reason.isBlank() ? null : reason.trim();
        this.createdAt = now;
    }

    public static BlockedTime forBranch(Branch branch, LocalDate startDate, LocalDate endDate,
                                        LocalTime startTime, LocalTime endTime, String reason, LocalDateTime now) {
        return new BlockedTime(branch, null, startDate, endDate, startTime, endTime, reason, now);
    }

    public static BlockedTime forTrainer(Trainer trainer, LocalDate startDate, LocalDate endDate,
                                         LocalTime startTime, LocalTime endTime, String reason, LocalDateTime now) {
        return new BlockedTime(null, trainer, startDate, endDate, startTime, endTime, reason, now);
    }

    public boolean isAllDay() {
        return startTime == null;
    }

    public boolean coversDate(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /** True if the half-open range [from, to) on {@code date} falls inside this block. */
    public boolean blocks(LocalDate date, LocalTime from, LocalTime to) {
        if (!coversDate(date)) {
            return false;
        }
        return isAllDay() || (from.isBefore(endTime) && startTime.isBefore(to));
    }

    public Long getId() {
        return id;
    }

    public Branch getBranch() {
        return branch;
    }

    public Trainer getTrainer() {
        return trainer;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
