package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.Size;

/** @param reason shown to members whose bookings are cancelled; optional */
public record DeactivateTrainerRequest(
        @Size(max = 300, message = "Reason is too long") String reason
) {
}
