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

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Owner-scoped lookups: another user's booking yields empty (404), so ids can't be probed.
    Optional<Booking> findByIdAndMemberId(Long id, Long memberId);

    Optional<Booking> findByIdAndTrainerId(Long id, Long trainerId);

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

    /** Ids of unanswered requests and unpaid accepted bookings past their deadline; the caller locks each one. */
    @Query("""
            select b.id from Booking b
            where (b.status = com.mycompany.gymbooking.model.BookingStatus.REQUESTED and b.respondByAt <= :now)
               or (b.status = com.mycompany.gymbooking.model.BookingStatus.ACCEPTED and b.payByAt <= :now)
            """)
    List<Long> findOverdueIds(@Param("now") LocalDateTime now);
}
