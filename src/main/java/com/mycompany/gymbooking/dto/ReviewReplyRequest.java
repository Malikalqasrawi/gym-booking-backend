package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewReplyRequest(
        @NotBlank(message = "Reply is required")
        @Size(max = 500, message = "Reply can be at most 500 characters")
        String reply
) {
}
