package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mycompany.gymbooking.model.Payment;
import com.mycompany.gymbooking.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Payment details, shown only to the member who paid. */
public record PaymentInfo(
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String method,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime paidAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime refundedAt
) {

    /** Returns null for a missing or still PENDING payment. */
    public static PaymentInfo from(Payment payment) {
        if (payment == null || payment.getStatus() == PaymentStatus.PENDING) {
            return null;
        }
        return new PaymentInfo(payment.getStatus(), payment.getAmount(), payment.getCurrency(),
                payment.getMethodLabel(), payment.getPaidAt(), payment.getRefundedAt());
    }
}
