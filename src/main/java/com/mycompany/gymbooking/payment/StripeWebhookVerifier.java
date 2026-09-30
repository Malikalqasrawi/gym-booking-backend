package com.mycompany.gymbooking.payment;

import com.mycompany.gymbooking.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Checks that a webhook message really comes from Stripe.
 *
 * Our webhook URL is public (Stripe can't log in), so anyone could POST a fake
 * "payment succeeded" to it. Stripe therefore SIGNS every message with a secret only
 * Stripe and we know (whsec_...), and sends the signature in a header:
 *
 *   Stripe-Signature: t=1727700000,v1=5257a869e7ec...
 *
 *   t  = when Stripe sent it (seconds since 1970)
 *   v1 = HMAC-SHA256(secret, t + "." + exact body), in hex
 *
 * We compute the same HMAC. Same result → the body wasn't changed and the sender knows the secret.
 * The time check stops someone from re-sending an old, real message later ("replay attack").
 */
public class StripeWebhookVerifier {

    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final byte[] secret;
    private final Clock clock;

    public StripeWebhookVerifier(String secret, Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public void verify(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw invalid();
        }

        // 1. Split "t=...,v1=...,v1=..." (Stripe may send several v1 values while you change secrets)
        long timestamp = -1;
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            if (keyValue[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(keyValue[1]);
                } catch (NumberFormatException e) {
                    throw invalid();
                }
            } else if (keyValue[0].equals("v1")) {
                signatures.add(keyValue[1]);
            }
        }
        if (timestamp < 0 || signatures.isEmpty()) {
            throw invalid();
        }

        // 2. Too old (or from the future)? Refuse.
        long ageSeconds = Math.abs(clock.instant().getEpochSecond() - timestamp);
        if (ageSeconds > TOLERANCE.toSeconds()) {
            throw new BadRequestException("WEBHOOK_TOO_OLD", "Webhook timestamp is outside the allowed 5 minutes");
        }

        // 3. Our own signature of "t.body"
        byte[] expected = hmacSha256(timestamp + "." + payload);

        // 4. Compare. MessageDigest.isEqual takes the same time whether the first or the last byte differs,
        //    so an attacker can't guess the signature byte by byte by measuring our response time.
        for (String signature : signatures) {
            byte[] given;
            try {
                given = HexFormat.of().parseHex(signature);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (MessageDigest.isEqual(expected, given)) {
                return;   // genuine
            }
        }
        throw invalid();
    }

    private byte[] hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private static BadRequestException invalid() {
        return new BadRequestException("INVALID_SIGNATURE", "Webhook signature is missing or wrong");
    }
}
