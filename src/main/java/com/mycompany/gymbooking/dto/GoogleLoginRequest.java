package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.NotBlank;

/** The ID token the app received from Google after the user picked an account. */
public record GoogleLoginRequest(
        @NotBlank(message = "ID token is required")
        String idToken
) {
}
