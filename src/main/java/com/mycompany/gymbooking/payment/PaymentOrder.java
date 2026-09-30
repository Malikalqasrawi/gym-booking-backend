package com.mycompany.gymbooking.payment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A charge request sent to the provider.
 *
 * @param idempotencyKey a retried request with the same key returns the original payment instead of a new one
 * @param metadata       internal ids stored with the payment at the provider
 */
public record PaymentOrder(String idempotencyKey,
                           BigDecimal amount,
                           String currency,
                           String description,
                           Map<String, String> metadata) {
}
