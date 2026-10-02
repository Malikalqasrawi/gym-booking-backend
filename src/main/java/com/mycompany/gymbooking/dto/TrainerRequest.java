package com.mycompany.gymbooking.dto;

import com.mycompany.gymbooking.model.Gender;
import com.mycompany.gymbooking.model.TrainingCategory;
import com.mycompany.gymbooking.phone.ValidPhone;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** A trainer's account and profile details, as entered by an admin. */
public record TrainerRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name is too long")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        @Size(max = 150, message = "Email is too long")
        String email,

        @NotBlank(message = "Phone number is required")
        @ValidPhone
        String phone,

        @NotNull(message = "Branch is required")
        Long branchId,

        @NotNull(message = "Category is required")
        TrainingCategory category,

        @NotNull(message = "Gender is required")
        Gender gender,

        @NotNull(message = "Hourly rate is required")
        @DecimalMin(value = "1", message = "Hourly rate must be at least 1 JOD")
        @DecimalMax(value = "500", message = "Hourly rate can't be more than 500 JOD")
        @Digits(integer = 3, fraction = 3, message = "Hourly rate can have up to 3 decimals")
        BigDecimal hourlyRate,

        @NotBlank(message = "Specialty is required")
        @Size(max = 100, message = "Specialty is too long")
        String specialty,

        @Size(max = 500, message = "Bio is too long")
        String bio,

        @NotNull(message = "Years of experience is required")
        @Min(value = 0, message = "Years of experience can't be negative")
        @Max(value = 60, message = "Years of experience can be at most 60")
        Integer yearsOfExperience,

        @Size(max = 100, message = "Languages is too long")
        String languages,

        @Size(max = 10, message = "Up to 10 tags")
        List<@NotBlank(message = "Tags can't be empty")
             @Size(max = 40, message = "Tags can be up to 40 characters") String> tags,

        @Size(max = 10, message = "Up to 10 certifications")
        List<@NotBlank(message = "Certifications can't be empty")
             @Size(max = 120, message = "Certifications can be up to 120 characters") String> certifications
) {
}
