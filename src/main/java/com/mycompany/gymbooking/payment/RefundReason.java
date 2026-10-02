package com.mycompany.gymbooking.payment;

public enum RefundReason {
    /** The member cancelled a paid session within the refund window. */
    MEMBER_CANCELLED,
    /** The gym cancelled the session; always a full refund. */
    GYM_CANCELLED,
    /** The payment arrived after the booking had expired or been cancelled. */
    PAID_TOO_LATE
}
