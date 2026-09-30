package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.model.Booking;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small text helpers for messages about a booking (shared by the booking and payment services). */
final class BookingTexts {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    private BookingTexts() {
    }

    /** "Wed 30 Sep, 10:00–11:00" */
    static String when(Booking booking) {
        return booking.getDate().format(DAY) + ", " + booking.getStartTime() + "–" + booking.getEndTime();
    }

    /** "Abdoun Branch, Abdoun Circle" */
    static String where(Booking booking) {
        return booking.getBranch().getName() + ", " + booking.getBranch().getAddress();
    }

    /** "Tue 29 Sep, 10:00" */
    static String dayAndTime(LocalDateTime time) {
        return time.format(DAY_AND_TIME);
    }
}
