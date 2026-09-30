package com.mycompany.gymbooking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Everything the app needs to open Stripe's payment screen for ONE booking.
 *
 * @param clientSecret           opens the payment screen for THIS payment only. Only the booking's member gets it.
 * @param publishableKey         Stripe's PUBLIC key (pk_test_...). Safe in the app: it can't take money out.
 * @param payBy                  pay before this, or the time is released
 * @param cancelUntilAfterPaying once paid, cancelling (with a refund) is possible until this moment.
 *                               If it's already in the past, the app warns "can't be cancelled after paying".
 * @param alreadyPaid            true = the money already arrived (e.g. the app closed right after paying):
 *                               skip the payment screen and just confirm
 */
public record PaymentStartResponse(
        Long bookingId,
        String clientSecret,
        String publishableKey,
        String merchantName,
        BigDecimal amount,
        String currency,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime payBy,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime cancelUntilAfterPaying,
        boolean alreadyPaid
) {
}
