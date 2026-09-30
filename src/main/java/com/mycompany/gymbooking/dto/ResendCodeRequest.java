package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** JSON body for POST /api/auth/resend-code  →  { "email": "..." } */
public record ResendCodeRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        String email
) {
}
