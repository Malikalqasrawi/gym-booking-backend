package com.mycompany.gymbooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO = "Data Transfer Object": the shape of the JSON the app sends us.
 *
 * A "record" is a short way to write a class whose only job is to hold data.
 * Java writes the constructor and getters for us: request.email(), request.password()...
 *
 * The annotations are validation rules. If one fails, Spring rejects the request
 * with 400 Bad Request before our code even runs.
 */
public record SignUpRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name is too long")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Phone must be 8-15 digits, optionally starting with +")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain a letter and a number")
        String password
) {
}
