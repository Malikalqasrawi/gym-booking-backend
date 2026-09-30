package com.mycompany.gymbooking.payment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * What we ask the provider to charge.
 *
 * @param idempotencyKey if this exact request is sent twice (e.g. the network dropped the first answer),
 *                       Stripe returns the SAME payment instead of creating a second one
 * @param amount         e.g. 20.000
 * @param currency       e.g. "JOD"
 * @param description    shown in the Stripe Dashboard
 * @param metadata       our own ids, saved on Stripe's side too (e.g. booking_id=58)
 */
public record PaymentOrder(String idempotencyKey,
                           BigDecimal amount,
                           String currency,
                           String description,
                           Map<String, String> metadata) {
}
