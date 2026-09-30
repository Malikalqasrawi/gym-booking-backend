package com.mycompany.gymbooking.payment;

import java.math.BigDecimal;

/**
 * "The money for booking X was given back."
 *
 * @param paidTooLate true = the booking had already expired/been cancelled when the payment arrived,
 *                    false = the member cancelled a paid session in time
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
