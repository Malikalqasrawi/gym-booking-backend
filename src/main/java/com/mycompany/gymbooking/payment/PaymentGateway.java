package com.mycompany.gymbooking.payment;

/** Payment provider abstraction used by the payment service. */
public interface PaymentGateway {

    /** Provider name stored on each payment. */
    String provider();

    /** Public key the app needs to open the provider's payment sheet. */
    String publishableKey();

    /** Creates a payment for the customer to complete; nothing is charged yet. */
    GatewayPayment createPayment(PaymentOrder order);

    /** Fetches the current state from the provider, which is the source of truth. */
    GatewayPayment getPayment(String providerPaymentId);

    /**
     * Refunds the full amount.
     * @param idempotencyKey repeated calls with the same key refund only once
     */
    GatewayRefund refund(String providerPaymentId, String idempotencyKey);
}
