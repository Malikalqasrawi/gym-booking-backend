package com.mycompany.gymbooking.dto;

/** Bookings a block would cancel, shown to the admin before confirming. */
public record BlockImpactResponse(int bookings, int paidBookings) {
}
