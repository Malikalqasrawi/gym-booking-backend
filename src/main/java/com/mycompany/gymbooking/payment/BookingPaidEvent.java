package com.mycompany.gymbooking.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Published when a booking is paid. Carries pre-formatted values because listeners run after the
 * transaction commits, when lazy entity associations can no longer be loaded.
 *
 * @param price  booking price in JOD
 * @param amount amount charged to the card, in {@code currency}
 */
public record BookingPaidEvent(Long bookingId,
                               String memberEmail,
                               String memberName,
                               String trainerEmail,
                               String trainerName,
                               String when,
                               String where,
                               BigDecimal price,
                               BigDecimal amount,
                               String currency,
                               String paymentMethod,
                               LocalDateTime paidAt,
                               LocalDateTime cancelUntil) {
}
