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
 * Verifies the Stripe-Signature header of a webhook request. The webhook endpoint is public, so
 * each payload must carry v1 = HMAC-SHA256(secret, t + "." + body). The timestamp tolerance
 * rejects replays of old, genuine messages.
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

        // Several v1 values may be present while the signing secret is being rolled.
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

        long ageSeconds = Math.abs(clock.instant().getEpochSecond() - timestamp);
        if (ageSeconds > TOLERANCE.toSeconds()) {
            throw new BadRequestException("WEBHOOK_TOO_OLD", "Webhook timestamp is outside the allowed 5 minutes");
        }

        byte[] expected = hmacSha256(timestamp + "." + payload);

        // Constant-time comparison to avoid leaking the signature through timing.
        for (String signature : signatures) {
            byte[] given;
            try {
                given = HexFormat.of().parseHex(signature);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (MessageDigest.isEqual(expected, given)) {
                return;
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
