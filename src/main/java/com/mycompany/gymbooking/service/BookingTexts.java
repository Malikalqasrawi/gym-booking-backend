package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.model.Booking;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatting helpers for booking messages, shared by the booking and payment services. */
final class BookingTexts {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    private BookingTexts() {
    }

    static String when(Booking booking) {
        return booking.getDate().format(DAY) + ", " + booking.getStartTime() + "–" + booking.getEndTime();
    }

    static String where(Booking booking) {
        return booking.getBranch().getName() + ", " + booking.getBranch().getAddress();
    }

    static String dayAndTime(LocalDateTime time) {
        return time.format(DAY_AND_TIME);
    }
}
