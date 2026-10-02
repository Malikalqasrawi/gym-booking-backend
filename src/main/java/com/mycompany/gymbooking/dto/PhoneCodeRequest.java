package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneCodeRequest(
        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^[0-9]{6}$", message = "Code must be 6 digits")
        String code
) {
}
