package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.BookingStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database queries for bookings. Spring Data writes the SQL from the method names
 * (with ? placeholders, so no SQL injection).
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // ------------------------------------------------------------------
    // OWNER-SCOPED lookups: "booking 58, but ONLY if it belongs to member 5".
    // Someone else's booking → empty → the service answers 404, as if it didn't exist.
    // This is what stops "change the id in the URL and see a stranger's booking".
    // ------------------------------------------------------------------
    Optional<Booking> findByIdAndMemberId(Long id, Long memberId);

    Optional<Booking> findByIdAndTrainerId(Long id, Long trainerId);

    // ------------------------------------------------------------------
    // LOCKED lookups (SELECT ... FOR UPDATE) for everything that touches money.
    // If the app confirms a payment while the member taps Cancel (or Stripe's message arrives),
    // the second one waits until the first has finished, then sees the new status.
    // ("Locked" in the name is just for us: Spring ignores the words between "find" and "By".)
    // ------------------------------------------------------------------
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Booking> findLockedByIdAndMemberId(Long id, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Booking> findLockedById(Long id);

    // ------------------------------------------------------------------
    // Lists. @EntityGraph loads the trainer, member and branch in the SAME query (a JOIN),
    // instead of one extra query per booking.
    // ------------------------------------------------------------------
    @EntityGraph(attributePaths = {"trainer", "member", "branch"})
    List<Booking> findByMemberIdOrderByDateDescStartTimeDesc(Long memberId);

    @EntityGraph(attributePaths = {"trainer", "member", "branch"})
    List<Booking> findByTrainerIdAndStatusOrderByRespondByAtAsc(Long trainerId, BookingStatus status);

    @EntityGraph(attributePaths = {"trainer", "member", "branch"})
    List<Booking> findByTrainerIdAndStatusInAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(
            Long trainerId, Collection<BookingStatus> statuses, LocalDate fromDate);

    // ------------------------------------------------------------------
    // Used for the "is this time free?" checks
    // ------------------------------------------------------------------
    List<Booking> findByTrainerIdAndDateAndStatusIn(Long trainerId, LocalDate date, Collection<BookingStatus> statuses);

    List<Booking> findByMemberIdAndDateAndStatusIn(Long memberId, LocalDate date, Collection<BookingStatus> statuses);

    List<Booking> findByMemberIdAndStatus(Long memberId, BookingStatus status);

    // ------------------------------------------------------------------
    // Used by the clean-up job: requests not answered in time + accepted bookings not paid in time.
    // Only the ids: the job then locks and updates them one by one.
    // ------------------------------------------------------------------
    @Query("""
            select b.id from Booking b
            where (b.status = com.mycompany.gymbooking.model.BookingStatus.REQUESTED and b.respondByAt <= :now)
               or (b.status = com.mycompany.gymbooking.model.BookingStatus.ACCEPTED and b.payByAt <= :now)
            """)
    List<Long> findOverdueIds(@Param("now") LocalDateTime now);
}
