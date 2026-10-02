package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.BookingRequest;
import com.mycompany.gymbooking.dto.BookingResponse;
import java.util.Collection;
import java.util.List;

/**
 * Booking operations for members and trainers. Each method takes the authenticated user's id and
 * only operates on that user's own bookings.
 */
public interface BookingService {

    BookingResponse requestSession(Long memberId, BookingRequest request);

    List<BookingResponse> myBookings(Long memberId);

    BookingResponse myBooking(Long memberId, Long bookingId);

    BookingResponse cancel(Long memberId, Long bookingId);

    List<BookingResponse> pendingRequests(Long trainerId);

    List<BookingResponse> upcomingSchedule(Long trainerId);

    BookingResponse accept(Long trainerId, Long bookingId, String message);

    BookingResponse reject(Long trainerId, Long bookingId, String message);

    /**
     * Cancels all of a trainer's bookings that haven't started, refunding paid ones in full, and
     * emails the members. Joins the caller's transaction.
     */
    GymCancellations cancelUpcomingForTrainer(Long trainerId, String note);

    /**
     * Cancels the given bookings that still hold a slot and haven't started, refunding paid ones in
     * full, and emails the members and trainers. Others are skipped. Joins the caller's transaction.
     */
    GymCancellations cancelByGym(Collection<Long> bookingIds, String note);

    /** Cancels one booking on the gym's side, with the same refund and emails. */
    void cancelByGym(Long bookingId, String note);

    /** Marks unanswered requests and unpaid accepted bookings as EXPIRED. Returns the number expired. */
    int expireOverdue();
}
