package com.mycompany.gymbooking.payment;

/** Where a payment is, in words that don't depend on the provider. */
public enum GatewayPaymentStatus {
    /** Not paid yet: the payment screen wasn't finished, or the card was declined (the customer can try again). */
    WAITING_FOR_CUSTOMER,
    /** The bank is still working on it. */
    PROCESSING,
    /** The money arrived. */
    SUCCEEDED,
    /** Cancelled at the provider: it can never be paid. */
    CANCELED
}
