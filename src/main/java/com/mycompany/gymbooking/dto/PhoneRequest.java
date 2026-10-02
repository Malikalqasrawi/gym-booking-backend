package com.mycompany.gymbooking.dto;

import com.mycompany.gymbooking.phone.ValidPhone;
import jakarta.validation.constraints.NotBlank;

public record PhoneRequest(
        @NotBlank(message = "Phone number is required")
        @ValidPhone
        String phone
) {
}
