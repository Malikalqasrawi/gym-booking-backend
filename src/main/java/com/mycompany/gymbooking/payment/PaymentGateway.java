package com.mycompany.gymbooking.payment;

/**
 * INTERFACE (abstraction): "something that can take and give back money".
 *
 * PaymentServiceImpl only talks to THIS interface, never to Stripe directly. So:
 *   - StripePaymentGateway is today's implementation (Stripe TEST mode)
 *   - another provider (e.g. a Jordanian bank's gateway) could be added later as a new class,
 *     without changing the booking or payment rules at all (polymorphism)
 */
public interface PaymentGateway {

    /** Short name saved on each payment, e.g. "stripe". */
    String provider();

    /** The PUBLIC key the app needs to open the provider's payment screen (Stripe: pk_test_...). */
    String publishableKey();

    /** Creates a payment the customer can then pay (nothing is charged yet). */
    GatewayPayment createPayment(PaymentOrder order);

    /** Asks the provider how this payment is doing right now (the only source of truth). */
    GatewayPayment getPayment(String providerPaymentId);

    /**
     * Gives the whole amount back.
     * @param idempotencyKey same key twice = the provider refunds only once (safe to retry)
     */
    GatewayRefund refund(String providerPaymentId, String idempotencyKey);
}
