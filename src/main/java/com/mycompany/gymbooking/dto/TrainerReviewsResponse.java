package com.mycompany.gymbooking.dto;

import java.util.List;

/** A trainer's rating (null without reviews) and their latest visible reviews. */
public record TrainerReviewsResponse(
        Double averageRating,
        long reviewCount,
        List<ReviewResponse> reviews
) {
}
