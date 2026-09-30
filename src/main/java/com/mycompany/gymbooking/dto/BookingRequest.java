package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What the app sends to request a session:
 * { "trainerId": 2, "date": "2026-10-04", "startTime": "10:00", "durationMinutes": 60, "note": "First time" }
 *
 * No memberId here on purpose: the member is ALWAYS taken from the login token,
 * so nobody can book in someone else's name.
 */
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
