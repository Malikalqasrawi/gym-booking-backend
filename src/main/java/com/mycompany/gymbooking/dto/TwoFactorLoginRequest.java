package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.NotBlank;

/** The second step of a two-factor login: the challenge from the login answer and a code. */
public record TwoFactorLoginRequest(
        @NotBlank(message = "Challenge token is required")
        String challengeToken,

        @NotBlank(message = "Code is required")
        String code
) {
}
