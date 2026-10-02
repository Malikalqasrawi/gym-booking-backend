package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AdminBookingResponse;
import com.mycompany.gymbooking.model.BookingStatus;
import java.util.List;

/** All bookings, for the gym's admin. */
public interface AdminBookingService {

    /**
     * Sessions that haven't ended (upcoming, soonest first) or have ended (past, latest first and
     * capped). Null filters match everything; {@code status} is the status right now.
     */
    List<AdminBookingResponse> list(boolean upcoming, Long branchId, Long trainerId, BookingStatus status);

    AdminBookingResponse get(Long bookingId);

    /** Cancels on the gym's side: a paid session is refunded in full, and the member and trainer are emailed. */
    AdminBookingResponse cancel(Long bookingId, String reason);
}
