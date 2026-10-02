package com.mycompany.gymbooking.dto;

/** A code was texted to {@code phone}; another can be requested after {@code resendAfterSeconds}. */
public record PhoneCodeSentResponse(
        String phone,
        long resendAfterSeconds,
        long expiresInMinutes
) {
}
