package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.BookingStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Owner-scoped lookups: another user's booking yields empty (404), so ids can't be probed.
    Optional<Booking> findByIdAndMemberId(Long id, Long memberId);

    Optional<Booking> findByIdAndTrainerId(Long id, Long trainerId);

    boolean existsByBranchId(Long branchId);

    // Row locks for payment-related changes, so a concurrent confirm, cancel or webhook
    // waits for the first transaction and then sees the updated status.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Booking> findLockedByIdAndMemberId(Long id, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Booking> findLockedById(Long id);

    @EntityGraph(attributePaths = {"trainer", "member", "branch"})
    List<Booking> findByMemberIdOrderByDateDescStartTimeDesc(Long memberId);

    @EntityGraph(attributePaths = {"trainer", "member", "branch"})
    List<Booking> findByTrainerIdAndStatusOrderByRespondByAtAsc(Long trainerId, BookingStatus status);

    @EntityGraph(attributePaths = {"trainer", "member", "branch"})
    List<Booking> findByTrainerIdAndStatusInAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(
            Long trainerId, Collection<BookingStatus> statuses, LocalDate fromDate);

    List<Booking> findByTrainerIdAndDateAndStatusIn(Long trainerId, LocalDate date, Collection<BookingStatus> statuses);

    List<Booking> findByMemberIdAndDateAndStatusIn(Long memberId, LocalDate date, Collection<BookingStatus> statuses);

    List<Booking> findByMemberIdAndStatus(Long memberId, BookingStatus status);

    /** Ids of a trainer's bookings that still hold a slot today or later; the caller locks and re-checks each one. */
    @Query("""
            select b.id from Booking b
            where b.trainer.id = :trainerId and b.status in :statuses and b.date >= :fromDate
            order by b.date, b.startTime
            """)
    List<Long> findIdsByTrainerFrom(@Param("trainerId") Long trainerId,
                                    @Param("statuses") Collection<BookingStatus> statuses,
                                    @Param("fromDate") LocalDate fromDate);

    /** Bookings in a date range for a trainer or at a branch that may still hold a slot; the caller filters by time. */
    @Query("""
            select b from Booking b
            where b.status in :statuses and b.date between :fromDate and :toDate
              and (b.trainer.id = :trainerId or b.branch.id = :branchId)
            order by b.date, b.startTime
            """)
    List<Booking> findForBlock(@Param("trainerId") Long trainerId,
                               @Param("branchId") Long branchId,
                               @Param("statuses") Collection<BookingStatus> statuses,
                               @Param("fromDate") LocalDate fromDate,
                               @Param("toDate") LocalDate toDate);

    interface BookingSlot {
        Long getId();

        LocalDate getDate();

        LocalTime getStartTime();

        LocalTime getEndTime();
    }

    /**
     * Same bookings as {@link #findForBlock}, as id and time only, so none are loaded before the
     * caller locks them.
     */
    @Query("""
            select b.id as id, b.date as date, b.startTime as startTime, b.endTime as endTime from Booking b
            where b.status in :statuses and b.date between :fromDate and :toDate
              and (b.trainer.id = :trainerId or b.branch.id = :branchId)
            order by b.date, b.startTime
            """)
    List<BookingSlot> findSlotsForBlock(@Param("trainerId") Long trainerId,
                                        @Param("branchId") Long branchId,
                                        @Param("statuses") Collection<BookingStatus> statuses,
                                        @Param("fromDate") LocalDate fromDate,
                                        @Param("toDate") LocalDate toDate);

    /** Admin list: sessions that haven't ended, soonest first. Null filters match everything. */
    @EntityGraph(attributePaths = {"trainer", "member", "branch"})
    @Query("""
            select b from Booking b
            where (b.date > :today or (b.date = :today and b.endTime > :time))
              and (:branchId is null or b.branch.id = :branchId)
              and (:trainerId is null or b.trainer.id = :trainerId)
            order by b.date, b.startTime
            """)
    List<Booking> findUpcomingForAdmin(@Param("today") LocalDate today,
                                       @Param("time") LocalTime time,
                                       @Param("branchId") Long branchId,
                                       @Param("trainerId") Long trainerId);

    /** Admin list: sessions that have ended, most recent first. */
    @EntityGraph(attributePaths = {"trainer", "member", "branch"})
    @Query("""
            select b from Booking b
            where (b.date < :today or (b.date = :today and b.endTime <= :time))
              and (:branchId is null or b.branch.id = :branchId)
              and (:trainerId is null or b.trainer.id = :trainerId)
            order by b.date desc, b.startTime desc
            """)
    List<Booking> findPastForAdmin(@Param("today") LocalDate today,
                                   @Param("time") LocalTime time,
                                   @Param("branchId") Long branchId,
                                   @Param("trainerId") Long trainerId,
                                   Pageable limit);

    interface TrainerBookingCount {
        Long getTrainerId();

        long getBookings();
    }

    /** Bookings per trainer that hold a slot and haven't started yet. */
    @Query("""
            select b.trainer.id as trainerId, count(b) as bookings from Booking b
            where b.status in :statuses
              and (b.date > :today or (b.date = :today and b.startTime > :time))
            group by b.trainer.id
            """)
    List<TrainerBookingCount> countUpcomingByTrainer(@Param("statuses") Collection<BookingStatus> statuses,
                                                     @Param("today") LocalDate today,
                                                     @Param("time") LocalTime time);

    /** Ids of unanswered requests and unpaid accepted bookings past their deadline; the caller locks each one. */
    @Query("""
            select b.id from Booking b
            where (b.status = com.mycompany.gymbooking.model.BookingStatus.REQUESTED and b.respondByAt <= :now)
               or (b.status = com.mycompany.gymbooking.model.BookingStatus.ACCEPTED and b.payByAt <= :now)
            """)
    List<Long> findOverdueIds(@Param("now") LocalDateTime now);

    /** Paid bookings on these days that SessionReminders hasn't dealt with yet; it locks each one. */
    @Query("""
            select b.id from Booking b
            where b.status = com.mycompany.gymbooking.model.BookingStatus.PAID and b.reminderCheckedAt is null
              and b.date between :from and :to
            """)
    List<Long> findReminderCandidateIds(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
