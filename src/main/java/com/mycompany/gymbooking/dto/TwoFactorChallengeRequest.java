package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorChallengeRequest(
        @NotBlank(message = "Challenge token is required")
        String challengeToken
) {
}
