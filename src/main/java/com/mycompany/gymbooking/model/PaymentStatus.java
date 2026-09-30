package com.mycompany.gymbooking.model;

/**
 * The life of a payment:
 *
 *   PENDING ──money arrived──► SUCCEEDED ──member cancels in time──► REFUNDED
 *
 * PENDING = Stripe knows about it and the payment screen can be opened, but nothing was charged yet.
 */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    REFUNDED
}
