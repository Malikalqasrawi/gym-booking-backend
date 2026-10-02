package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.Review;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBookingId(Long bookingId);

    List<Review> findByBookingIdIn(Collection<Long> bookingIds);

    /** What a trainer's profile shows: visible reviews, newest first. */
    @EntityGraph(attributePaths = {"member", "booking"})
    List<Review> findByTrainerIdAndHiddenFalseOrderByCreatedAtDesc(Long trainerId, Pageable limit);

    @EntityGraph(attributePaths = {"member", "trainer", "booking"})
    Optional<Review> findWithDetailsById(Long id);

    /** For the admin: every review, or only hidden or only visible ones, newest first. */
    @EntityGraph(attributePaths = {"member", "trainer", "booking"})
    @Query("select r from Review r where :hidden is null or r.hidden = :hidden order by r.createdAt desc")
    List<Review> findForAdmin(@Param("hidden") Boolean hidden, Pageable limit);

    /** Average and number of visible reviews for each of these trainers that has any. */
    @Query("""
            select r.trainer.id as trainerId, avg(r.rating) as average, count(r) as reviews
            from Review r
            where r.hidden = false and r.trainer.id in :trainerIds
            group by r.trainer.id
            """)
    List<TrainerRating> ratingsFor(@Param("trainerIds") Collection<Long> trainerIds);

    interface TrainerRating {
        Long getTrainerId();

        Double getAverage();

        long getReviews();
    }
}
