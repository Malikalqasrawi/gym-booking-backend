package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.NotBlank;

/** Turns two-factor authentication off: the password plus a code from the app or a recovery code. */
public record TwoFactorDisableRequest(
        @NotBlank(message = "Password is required")
        String password,

        @NotBlank(message = "Code is required")
        String code
) {
}
