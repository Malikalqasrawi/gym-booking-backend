package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.BookingResponse;
import com.mycompany.gymbooking.dto.PaymentStartResponse;
import com.mycompany.gymbooking.security.SecurityUser;
import com.mycompany.gymbooking.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paying for a booking.
 *
 *   POST /api/bookings/{id}/payment          (member) → clientSecret etc. to open Stripe's payment screen
 *   POST /api/bookings/{id}/payment/confirm  (member) → we ask Stripe; if paid, the booking becomes PAID
 *   POST /api/payments/stripe/webhook        (Stripe) → Stripe's own "payment succeeded" message.
 *                                              No login (Stripe can't log in); checked by signature instead.
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/bookings/{id}/payment")
    public PaymentStartResponse start(@AuthenticationPrincipal SecurityUser me, @PathVariable Long id) {
        return paymentService.startPayment(me.getUser().getId(), id);
    }

    @PostMapping("/api/bookings/{id}/payment/confirm")
    public BookingResponse confirm(@AuthenticationPrincipal SecurityUser me, @PathVariable Long id) {
        return paymentService.confirmPayment(me.getUser().getId(), id);
    }

    /**
     * The body is read as a plain String on purpose: the signature is calculated over the EXACT bytes
     * Stripe sent. Turning it into an object and back could change spaces or field order → wrong signature.
     */
    @PostMapping("/api/payments/stripe/webhook")
    public ResponseEntity<Void> stripeWebhook(@RequestBody String payload,
                                              @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        paymentService.handleStripeWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
