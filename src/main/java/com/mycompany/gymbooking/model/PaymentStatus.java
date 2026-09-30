package com.mycompany.gymbooking.model;

/** PENDING means the payment exists at Stripe but nothing has been charged yet. */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    REFUNDED
}
