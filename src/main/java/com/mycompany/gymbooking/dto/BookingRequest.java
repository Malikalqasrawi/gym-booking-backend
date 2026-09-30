package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/** Has no member id: the member is always taken from the auth token. */
public record BookingRequest(

        @NotNull(message = "Trainer is required")
        Long trainerId,

        @NotNull(message = "Date is required")
        LocalDate date,

        @NotNull(message = "Start time is required")
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @NotNull(message = "Duration is required")
        Integer durationMinutes,

        @Size(max = 300, message = "The note can be at most 300 characters")
        String note
) {
}
