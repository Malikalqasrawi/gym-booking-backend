package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional message from the trainer when accepting or rejecting:
 * { "message": "See you at the free-weights area!" }   or   { "message": "I'm away that day, sorry" }
 */
public record TrainerReplyRequest(

        @Size(max = 300, message = "The message can be at most 300 characters")
        String message
) {
}
