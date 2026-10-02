package com.mycompany.gymbooking.dto;

/**
 * @param cancelledBookings upcoming bookings cancelled because the trainer left
 * @param refundedBookings  how many of those had been paid and were refunded in full
 */
public record TrainerDeactivationResponse(
        AdminTrainerResponse trainer,
        int cancelledBookings,
        int refundedBookings
) {
}
