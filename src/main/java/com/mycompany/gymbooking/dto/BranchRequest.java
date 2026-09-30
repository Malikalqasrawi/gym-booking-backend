package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/**
 * The JSON the admin sends to CREATE (POST) or UPDATE (PUT) a branch:
 * {
 *   "name": "Abdoun Branch",
 *   "address": "Abdoun Circle, Cairo St. 12",
 *   "city": "Amman",
 *   "latitude": 31.9454,
 *   "longitude": 35.8818,
 *   "phone": "+96265000001",
 *   "openingTime": "06:00",
 *   "closingTime": "23:00"
 * }
 */
public record BranchRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name is too long")
        String name,

        @NotBlank(message = "Address is required")
        @Size(max = 200, message = "Address is too long")
        String address,

        @NotBlank(message = "City is required")
        @Size(max = 60, message = "City is too long")
        String city,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        Double longitude,

        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Phone must be 8-15 digits, optionally starting with +")
        String phone,

        @NotNull(message = "Opening time is required")
        @JsonFormat(pattern = "HH:mm")
        LocalTime openingTime,

        @NotNull(message = "Closing time is required")
        @JsonFormat(pattern = "HH:mm")
        LocalTime closingTime
) {
}
