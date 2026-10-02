package com.mycompany.gymbooking.payment;

import java.math.BigDecimal;

/**
 * Published when a booking payment is refunded.
 *
 * @param note the gym's cancellation note, if any
 */
public record BookingRefundedEvent(Long bookingId,
                                   String memberEmail,
                                   String memberName,
                                   String trainerName,
                                   String when,
                                   BigDecimal amount,
                                   String currency,
                                   String paymentMethod,
                                   RefundReason reason,
                                   String note) {
}
