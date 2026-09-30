package com.mycompany.gymbooking.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The answer to "when is this trainer free on this day, for a session this long?"
 * {
 *   "trainerId": 2, "trainerName": "Sara Haddad",
 *   "branchId": 1, "branchName": "Abdoun Branch",
 *   "date": "2026-10-04", "durationMinutes": 60,
 *   "slots": [ { "start": "08:00", "end": "09:00" }, { "start": "08:30", "end": "09:30" }, ... ]
 * }
 * An empty "slots" list means: no free time that day (day off, or everything is taken).
 */
public record AvailabilityResponse(
        Long trainerId,
        String trainerName,
        Long branchId,
        String branchName,
        LocalDate date,
        int durationMinutes,
        List<TimeSlotResponse> slots
) {
}
