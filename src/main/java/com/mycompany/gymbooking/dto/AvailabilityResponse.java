package com.mycompany.gymbooking.dto;

import java.time.LocalDate;
import java.util.List;

/** {@code closedReason} is set when the branch is closed or the trainer is off for the whole day. */
public record AvailabilityResponse(
        Long trainerId,
        String trainerName,
        Long branchId,
        String branchName,
        LocalDate date,
        int durationMinutes,
        List<TimeSlotResponse> slots,
        String closedReason
) {
}
