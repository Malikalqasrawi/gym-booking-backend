package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Starts setting up an authenticator app from the Profile tab. {@code code} is only needed when
 * two-factor authentication is already on (moving to a new phone): a code from the current app,
 * or a recovery code.
 */
public record TwoFactorSetupRequest(
        @NotBlank(message = "Password is required")
        String password,

        String code
) {
}
