package com.mycompany.gymbooking.dto;

import java.time.LocalDate;
import java.util.List;

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
