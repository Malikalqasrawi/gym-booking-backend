package com.mycompany.gymbooking.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * "Booking X was paid" — published by PaymentServiceImpl, heard by PaymentEmailListener.
 *
 * It carries ready-made text (names, times) because the listener runs AFTER the transaction
 * has ended, when the database objects can't load anything any more.
 */
public record BookingPaidEvent(Long bookingId,
                               String memberEmail,
                               String memberName,
                               String trainerEmail,
                               String trainerName,
                               String when,
                               String where,
                               BigDecimal amount,
                               String currency,
                               String paymentMethod,
                               LocalDateTime paidAt,
                               LocalDateTime cancelUntil) {
}
