package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mycompany.gymbooking.model.Payment;
import com.mycompany.gymbooking.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The receipt part of a booking: { "status": "SUCCEEDED", "amount": 20.000, "method": "Visa •••• 4242", ... }
 * Only for the member who paid (trainers see the booking status PAID, not the card).
 */
public record PaymentInfo(
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String method,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime paidAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime refundedAt
) {

    /** null while nothing was paid yet (a PENDING payment is not interesting for the app). */
    public static PaymentInfo from(Payment payment) {
        if (payment == null || payment.getStatus() == PaymentStatus.PENDING) {
            return null;
        }
        return new PaymentInfo(payment.getStatus(), payment.getAmount(), payment.getCurrency(),
                payment.getMethodLabel(), payment.getPaidAt(), payment.getRefundedAt());
    }
}
