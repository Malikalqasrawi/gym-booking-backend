package com.mycompany.gymbooking.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A background task: once a minute, these are marked EXPIRED in the database and the member gets a message:
 *   - requests the trainer didn't answer in time (24 h)
 *   - accepted bookings the member didn't pay in time (12 h)
 *
 * The app is correct even between two runs: Booking.statusAt(now) already treats them as EXPIRED,
 * and availability already frees their time. This job just makes the database match and sends the messages.
 *
 * @Scheduled only works because TimeConfig has @EnableScheduling.
 */
@Component
public class BookingExpiryJob {

    private final BookingService bookingService;

    public BookingExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** fixedDelay: wait 60 s AFTER the previous run finished (runs never overlap). */
    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void expireOverdueBookings() {
        bookingService.expireOverdue();
    }
}
