package com.mycompany.gymbooking.payment;

import java.math.BigDecimal;

/**
 * A payment as reported by the provider.
 *
 * @param clientSecret lets the app complete this one payment and nothing else
 * @param cardBrand    set only once paid
 * @param cardLast4    set only once paid
 */
public record GatewayPayment(String id,
                             String clientSecret,
                             GatewayPaymentStatus status,
                             BigDecimal amount,
                             String currency,
                             String cardBrand,
                             String cardLast4) {
}
