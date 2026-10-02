package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.BookingResponse;
import com.mycompany.gymbooking.dto.PaymentStartResponse;
import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.Payment;
import com.mycompany.gymbooking.payment.RefundReason;
import java.time.LocalDateTime;

/**
 * Stripe payments for accepted bookings, and refunds. The client pays through Stripe's own UI with
 * the client secret from startPayment; the booking is then marked PAID by either confirmPayment or
 * the webhook, whichever arrives first. The other call is a no-op.
 */
public interface PaymentService {

    PaymentStartResponse startPayment(Long memberId, Long bookingId);

    BookingResponse confirmPayment(Long memberId, Long bookingId);

    void handleStripeWebhook(String payload, String signatureHeader);

    /** Refunds a cancelled booking that was PAID, in full. Returns the refunded payment. */
    Payment refundCancelledBooking(Booking booking, RefundReason reason, LocalDateTime now);
}
