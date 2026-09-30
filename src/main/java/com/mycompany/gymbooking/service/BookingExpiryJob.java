package com.mycompany.gymbooking.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Persists the EXPIRED status for unanswered requests and unpaid accepted bookings, and notifies
 * the members. Reads are already correct between runs because Booking.statusAt(now) treats overdue
 * bookings as expired; this job only brings the stored status in line.
 */
@Component
public class BookingExpiryJob {

    private final BookingService bookingService;

    public BookingExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void expireOverdueBookings() {
        bookingService.expireOverdue();
    }
}
