package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.Size;

public record TrainerReplyRequest(

        @Size(max = 300, message = "The message can be at most 300 characters")
        String message
) {
}
