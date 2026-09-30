package com.mycompany.gymbooking.model;

/**
 * The life of a booking:
 *
 *   REQUESTED ──accept──► ACCEPTED ──pay──► PAID
 *       │  └──reject───► REJECTED   │
 *       │  └──24 h, no answer──► EXPIRED ◄──12 h, not paid──┘
 *       └─────member cancels──► CANCELLED  (also from ACCEPTED, and from PAID until 24 h before)
 *
 * Saved in MySQL as text ("ACCEPTED"), not as a number, so the table is readable
 * and adding a new status later can't shift the meaning of old rows.
 */
public enum BookingStatus {
    REQUESTED,
    ACCEPTED,
    PAID,
    REJECTED,
    CANCELLED,
    EXPIRED
}
