package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.ReviewRequest;
import com.mycompany.gymbooking.dto.ReviewResponse;
import com.mycompany.gymbooking.dto.TrainerReviewsResponse;
import com.mycompany.gymbooking.exception.ConflictException;
import com.mycompany.gymbooking.exception.NotFoundException;
import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.Review;
import com.mycompany.gymbooking.notification.NotificationSender;
import com.mycompany.gymbooking.repository.BookingRepository;
import com.mycompany.gymbooking.repository.ReviewRepository;
import com.mycompany.gymbooking.repository.ReviewRepository.TrainerRating;
import com.mycompany.gymbooking.repository.TrainerRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Members rate a session after it took place: 1 to 5 stars and an optional comment, final once
 * sent. Trainers can answer a review, and the admin can hide one (the member is emailed why).
 * Hidden reviews don't count toward a trainer's average.
 */
@Service
public class ReviewService {

    /** Profiles show this many of the latest reviews. */
    private static final int PROFILE_REVIEWS = 50;
    private static final int ADMIN_REVIEWS = 200;

    private final ReviewRepository reviews;
    private final BookingRepository bookings;
    private final TrainerRepository trainers;
    private final NotificationSender notificationSender;
    private final Clock clock;
    private final long daysToReview;

    public ReviewService(ReviewRepository reviews,
                         BookingRepository bookings,
                         TrainerRepository trainers,
                         NotificationSender notificationSender,
                         Clock clock,
                         @Value("${app.reviews.days-to-review}") long daysToReview) {
        this.reviews = reviews;
        this.bookings = bookings;
        this.trainers = trainers;
        this.notificationSender = notificationSender;
        this.clock = clock;
        this.daysToReview = daysToReview;
    }

    @Transactional
    public ReviewResponse create(Long memberId, Long bookingId, ReviewRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = bookings.findByIdAndMemberId(bookingId, memberId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking not found"));
        if (reviews.existsByBookingId(bookingId)) {
            throw alreadyReviewed();
        }
        if (!booking.canBeReviewedAt(now, daysToReview)) {
            throw new ConflictException("CANNOT_REVIEW", now.isBefore(booking.getEndsAt())
                    ? "You can rate a paid session once it has taken place."
                    : "Sessions can be rated for " + daysToReview + " days after they take place.");
        }
        try {
            return ReviewResponse.publicView(reviews.saveAndFlush(new Review(booking, request.rating(), request.comment(), now)));
        } catch (DataIntegrityViolationException e) {
            throw alreadyReviewed();   // a second request for the same session at the same moment
        }
    }

    /** The trainer's average and latest visible reviews, for their profile. */
    @Transactional(readOnly = true)
    public TrainerReviewsResponse forTrainer(Long trainerId) {
        if (!trainers.existsById(trainerId)) {
            throw new NotFoundException("TRAINER_NOT_FOUND", "Trainer not found");
        }
        TrainerRating rating = reviews.ratingsFor(List.of(trainerId)).stream().findFirst().orElse(null);
        List<ReviewResponse> latest = reviews
                .findByTrainerIdAndHiddenFalseOrderByCreatedAtDesc(trainerId, PageRequest.of(0, PROFILE_REVIEWS))
                .stream()
                .map(ReviewResponse::publicView)
                .toList();
        return new TrainerReviewsResponse(
                rating == null ? null : Math.round(rating.getAverage() * 10) / 10.0,
                rating == null ? 0 : rating.getReviews(),
                latest);
    }

    /** A trainer answers a review of one of their sessions; answering again replaces the answer. */
    @Transactional
    public ReviewResponse reply(Long trainerId, Long reviewId, String text) {
        Review review = reviews.findWithDetailsById(reviewId)
                .filter(found -> found.getTrainer().getId().equals(trainerId))
                .orElseThrow(() -> new NotFoundException("REVIEW_NOT_FOUND", "Review not found"));
        if (review.isHidden()) {
            throw new ConflictException("REVIEW_HIDDEN", "The gym hid this review, so it can't be answered.");
        }
        review.reply(text, LocalDateTime.now(clock));
        return ReviewResponse.publicView(review);
    }

    /** For the admin, newest first; {@code hidden} null means all. */
    @Transactional(readOnly = true)
    public List<ReviewResponse> forAdmin(Boolean hidden) {
        return reviews.findForAdmin(hidden, PageRequest.of(0, ADMIN_REVIEWS)).stream()
                .map(ReviewResponse::adminView)
                .toList();
    }

    /** Hides the review from the trainer's profile and average, and emails the member the reason. */
    @Transactional
    public ReviewResponse hide(Long reviewId, String reason) {
        Review review = adminReview(reviewId);
        if (!review.isHidden()) {
            review.hide(reason, LocalDateTime.now(clock));
            String trainer = review.getTrainer().getFullName();
            notificationSender.send(review.getMember().getEmail(), "Your review of " + trainer + " was hidden",
                    "Hi " + review.getMember().getFullName().split(" ")[0] + ",\n\n"
                            + "The gym hid your review of your session with " + trainer + " on "
                            + BookingTexts.when(review.getBooking()) + ", so it no longer shows on their profile.\n\n"
                            + "  Reason:    " + review.getHiddenReason() + "\n\n"
                            + "If you think this is a mistake, reply to this email.");
        }
        return ReviewResponse.adminView(review);
    }

    /** Shows a hidden review again, e.g. one hidden by mistake. */
    @Transactional
    public ReviewResponse show(Long reviewId) {
        Review review = adminReview(reviewId);
        review.show();
        return ReviewResponse.adminView(review);
    }

    private Review adminReview(Long reviewId) {
        return reviews.findWithDetailsById(reviewId)
                .orElseThrow(() -> new NotFoundException("REVIEW_NOT_FOUND", "Review not found"));
    }

    private static ConflictException alreadyReviewed() {
        return new ConflictException("ALREADY_REVIEWED", "You already rated this session.");
    }
}
