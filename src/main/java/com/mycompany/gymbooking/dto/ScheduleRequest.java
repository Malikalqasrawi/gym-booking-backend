package com.mycompany.gymbooking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** A trainer's complete weekly schedule; replaces the current one. An empty list means no hours. */
public record ScheduleRequest(
        @NotNull(message = "Blocks are required")
        @Size(max = 50, message = "Up to 50 blocks")
        List<@Valid @NotNull(message = "Each block needs a day, start time and end time") WorkingHoursRequest> blocks
) {
}
