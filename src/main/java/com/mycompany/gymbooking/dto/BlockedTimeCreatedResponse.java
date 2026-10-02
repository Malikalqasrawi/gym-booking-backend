package com.mycompany.gymbooking.dto;

public record BlockedTimeCreatedResponse(
        BlockedTimeResponse blockedTime,
        int cancelledBookings,
        int refundedBookings
) {
}
