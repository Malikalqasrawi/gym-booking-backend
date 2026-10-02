package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The reason is emailed to the member who wrote the review. */
public record HideReviewRequest(
        @NotBlank(message = "Reason is required")
        @Size(max = 300, message = "Reason can be at most 300 characters")
        String reason
) {
}
