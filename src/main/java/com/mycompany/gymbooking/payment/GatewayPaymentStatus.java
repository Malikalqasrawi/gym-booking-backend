package com.mycompany.gymbooking.payment;

/** Provider-independent payment status. */
public enum GatewayPaymentStatus {
    /** Not completed or declined; the customer can retry. */
    WAITING_FOR_CUSTOMER,
    PROCESSING,
    SUCCEEDED,
    /** Cancelled at the provider; can never be paid. */
    CANCELED
}
