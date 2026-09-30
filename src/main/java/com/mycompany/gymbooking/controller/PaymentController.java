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
     * Public endpoint (Stripe cannot authenticate); requests are verified by signature instead.
     * The body is kept as a raw String because the signature covers the exact bytes Stripe sent.
     */
    @PostMapping("/api/payments/stripe/webhook")
    public ResponseEntity<Void> stripeWebhook(@RequestBody String payload,
                                              @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        paymentService.handleStripeWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
