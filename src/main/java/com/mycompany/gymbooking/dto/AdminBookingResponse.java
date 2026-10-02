package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.Payment;
import java.time.LocalDateTime;

/**
 * A booking as the gym's admin sees it: the member's view plus contact details. The booking
 * fields are flattened into the same JSON object. {@code gymCanCancel} ignores the members' 24 h
 * rule, since the gym can cancel until the session starts.
 */
public record AdminBookingResponse(
        @JsonUnwrapped BookingResponse booking,
        String memberEmail,
        String memberPhone,
        String trainerEmail,
        boolean gymCanCancel
) {

    public static AdminBookingResponse from(Booking booking, Payment payment, LocalDateTime now) {
        return new AdminBookingResponse(
                BookingResponse.from(booking, payment, now),
                booking.getMember().getEmail(),
                booking.getMember().getPhone(),
                booking.getTrainer().getEmail(),
                booking.holdsSlotAt(now) && now.isBefore(booking.getStartsAt()));
    }
}
