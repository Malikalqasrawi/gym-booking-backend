package com.mycompany.gymbooking.model;

/**
 * Booking lifecycle: REQUESTED -> ACCEPTED -> PAID. A booking can also end as REJECTED, CANCELLED
 * or EXPIRED (not answered or not paid in time).
 */
public enum BookingStatus {
    REQUESTED,
    ACCEPTED,
    PAID,
    REJECTED,
    CANCELLED,
    EXPIRED
}
