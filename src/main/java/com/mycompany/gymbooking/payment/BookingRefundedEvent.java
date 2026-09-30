package com.mycompany.gymbooking.payment;

import java.math.BigDecimal;

/**
 * Published when a booking payment is refunded.
 *
 * @param paidTooLate true if the payment arrived after the booking had expired or been cancelled;
 *                    false if the member cancelled a paid session in time
 */
public record BookingRefundedEvent(Long bookingId,
                                   String memberEmail,
                                   String memberName,
                                   String trainerName,
                                   String when,
                                   BigDecimal amount,
                                   String currency,
                                   String paymentMethod,
                                   boolean paidTooLate) {
}
