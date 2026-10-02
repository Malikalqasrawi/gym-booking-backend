package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.NotBlank;

/** A 6-digit code from the authenticator app that is being set up. */
public record TwoFactorCodeRequest(
        @NotBlank(message = "Code is required")
        String code
) {
}
