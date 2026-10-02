package com.mycompany.gymbooking.service;

/** Result of cancelling bookings on the gym's side. */
public record GymCancellations(int cancelled, int refunded) {
}
