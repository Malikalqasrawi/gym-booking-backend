package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be 1 to 5 stars")
        @Max(value = 5, message = "Rating must be 1 to 5 stars")
        Integer rating,

        @Size(max = 500, message = "Comment can be at most 500 characters")
        String comment
) {
}
