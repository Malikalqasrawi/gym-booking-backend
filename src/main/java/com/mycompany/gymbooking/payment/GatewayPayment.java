package com.mycompany.gymbooking.payment;

import java.math.BigDecimal;

/**
 * A payment as the provider sees it.
 *
 * @param id           the provider's id, e.g. "pi_3Q1x..."
 * @param clientSecret lets the APP open the payment screen for THIS payment only (it can't do anything else)
 * @param cardBrand    "visa" (only once paid)
 * @param cardLast4    "4242" (only once paid)
 */
public record GatewayPayment(String id,
                             String clientSecret,
                             GatewayPaymentStatus status,
                             BigDecimal amount,
                             String currency,
                             String cardBrand,
                             String cardLast4) {
}
