package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.HideReviewRequest;
import com.mycompany.gymbooking.dto.ReviewReplyRequest;
import com.mycompany.gymbooking.dto.ReviewRequest;
import com.mycompany.gymbooking.dto.ReviewResponse;
import com.mycompany.gymbooking.dto.TrainerReviewsResponse;
import com.mycompany.gymbooking.security.SecurityUser;
import com.mycompany.gymbooking.service.ReviewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reviews: members rate under /api/bookings, everyone reads under /api/trainers, trainers reply
 * under /api/trainer and the admin moderates under /api/admin. SecurityConfig checks the roles by path.
 */
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/bookings/{bookingId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse rateSession(@AuthenticationPrincipal SecurityUser me, @PathVariable Long bookingId,
                                      @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(me.getUser().getId(), bookingId, request);
    }

    @GetMapping("/trainers/{trainerId}/reviews")
    public TrainerReviewsResponse trainerReviews(@PathVariable Long trainerId) {
        return reviewService.forTrainer(trainerId);
    }

    @GetMapping("/trainer/reviews")
    public TrainerReviewsResponse myReviews(@AuthenticationPrincipal SecurityUser me) {
        return reviewService.forTrainer(me.getUser().getId());
    }

    @PutMapping("/trainer/reviews/{reviewId}/reply")
    public ReviewResponse reply(@AuthenticationPrincipal SecurityUser me, @PathVariable Long reviewId,
                                @Valid @RequestBody ReviewReplyRequest request) {
        return reviewService.reply(me.getUser().getId(), reviewId, request.reply());
    }

    /** {@code ?hidden=true} or {@code false} filters; without it, all reviews. */
    @GetMapping("/admin/reviews")
    public List<ReviewResponse> allReviews(@RequestParam(required = false) Boolean hidden) {
        return reviewService.forAdmin(hidden);
    }

    @PostMapping("/admin/reviews/{reviewId}/hide")
    public ReviewResponse hide(@PathVariable Long reviewId, @Valid @RequestBody HideReviewRequest request) {
        return reviewService.hide(reviewId, request.reason());
    }

    @PostMapping("/admin/reviews/{reviewId}/show")
    public ReviewResponse show(@PathVariable Long reviewId) {
        return reviewService.show(reviewId);
    }
}
