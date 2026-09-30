package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.BookingResponse;
import com.mycompany.gymbooking.dto.PaymentStartResponse;
import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.Payment;
import java.time.LocalDateTime;

/**
 * Paying for an accepted booking, and refunds.
 *
 * The flow:
 *   1. startPayment    → the app gets a clientSecret and opens Stripe's payment screen
 *   2. (the member types the card into STRIPE's screen; our server never sees the card)
 *   3. confirmPayment  → we ask Stripe "did it succeed?" and mark the booking PAID
 *      (Stripe can also tell us itself: handleStripeWebhook. Whichever comes first wins; the other does nothing.)
 */
public interface PaymentService {

    PaymentStartResponse startPayment(Long memberId, Long bookingId);

    BookingResponse confirmPayment(Long memberId, Long bookingId);

    /** Stripe's own "payment succeeded" message (only if the webhook is set up). */
    void handleStripeWebhook(String payload, String signatureHeader);

    /** Called by BookingService when a member cancels a PAID booking in time. Returns the refunded payment. */
    Payment refundCancelledBooking(Booking booking, LocalDateTime now);
}
