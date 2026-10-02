package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record WorkingHoursRequest(
        @NotNull(message = "Day is required") DayOfWeek dayOfWeek,
        @NotNull(message = "Start time is required") @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @NotNull(message = "End time is required") @JsonFormat(pattern = "HH:mm") LocalTime endTime
) {
}
