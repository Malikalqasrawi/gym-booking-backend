package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Blocks a branch or a trainer (set exactly one id) from startDate to endDate. Leave both times
 * empty for whole days, or set both to block the same hours on each of those days.
 */
public record BlockedTimeRequest(
        Long branchId,
        Long trainerId,
        @NotNull(message = "Start date is required") LocalDate startDate,
        @NotNull(message = "End date is required") LocalDate endDate,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @Size(max = 200, message = "Reason is too long") String reason
) {
}
