package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data the app needs to open Stripe's payment sheet for a booking.
 *
 * @param clientSecret           scoped to this payment; only returned to the booking's member
 * @param publishableKey         Stripe public key, safe to ship in the app
 * @param price                  booking price in JOD
 * @param amount                 amount the card is charged, in {@code currency}
 * @param payBy                  deadline after which the slot is released
 * @param cancelUntilAfterPaying last moment a paid booking can be refunded; may already be in the past
 * @param alreadyPaid            payment already succeeded, so the app should skip the sheet and confirm
 */
public record PaymentStartResponse(
        Long bookingId,
        String clientSecret,
        String publishableKey,
        String merchantName,
        BigDecimal price,
        BigDecimal amount,
        String currency,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime payBy,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime cancelUntilAfterPaying,
        boolean alreadyPaid
) {
}
