package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.BookingRequest;
import com.mycompany.gymbooking.dto.BookingResponse;
import java.util.List;

/**
 * WHAT can be done with bookings. BookingServiceImpl says HOW.
 *
 * Every method takes the id of the logged-in user (from the token) and only ever
 * touches that user's own bookings.
 */
public interface BookingService {

    // ---- Member ----
    BookingResponse requestSession(Long memberId, BookingRequest request);

    List<BookingResponse> myBookings(Long memberId);

    BookingResponse myBooking(Long memberId, Long bookingId);

    BookingResponse cancel(Long memberId, Long bookingId);

    // ---- Trainer ----
    List<BookingResponse> pendingRequests(Long trainerId);

    List<BookingResponse> upcomingSchedule(Long trainerId);

    BookingResponse accept(Long trainerId, Long bookingId, String message);

    BookingResponse reject(Long trainerId, Long bookingId, String message);

    // ---- Housekeeping (called by BookingExpiryJob) ----
    /** Requests not answered in time, and accepted bookings not paid in time → EXPIRED. Returns how many. */
    int expireOverdue();
}
