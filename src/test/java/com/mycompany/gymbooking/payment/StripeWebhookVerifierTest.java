package com.mycompany.gymbooking.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.gymbooking.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Only messages signed with OUR webhook secret, and less than 5 minutes old, are accepted. */
class StripeWebhookVerifierTest {

    private static final String SECRET = "whsec_test_secret";
    private static final long NOW = 1_790_000_000L;   // seconds since 1970
    private static final String BODY = "{\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_1\"}}}";

    private final StripeWebhookVerifier verifier =
            new StripeWebhookVerifier(SECRET, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));

    /** What Stripe does: HMAC-SHA256(secret, "timestamp.body") in hex. */
    static String sign(String secret, long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    private String codeOf(String body, String header) {
        return assertThrows(BadRequestException.class, () -> verifier.verify(body, header)).getCode();
    }

    @Test
    @DisplayName("a genuine Stripe message is accepted")
    void genuineMessage() throws Exception {
        verifier.verify(BODY, "t=" + NOW + ",v1=" + sign(SECRET, NOW, BODY));   // no exception = accepted
    }

    @Test
    @DisplayName("accepted if ANY of several v1 signatures matches (Stripe does this while you change secrets)")
    void oneOfSeveralSignatures() throws Exception {
        verifier.verify(BODY, "t=" + NOW + ",v1=" + sign("whsec_old", NOW, BODY) + ",v1=" + sign(SECRET, NOW, BODY));
    }

    @Test
    @DisplayName("a changed body is refused (e.g. someone swapped the payment id)")
    void tamperedBody() throws Exception {
        String header = "t=" + NOW + ",v1=" + sign(SECRET, NOW, BODY);
        assertEquals("INVALID_SIGNATURE", codeOf(BODY.replace("pi_1", "pi_2"), header));
    }

    @Test
    @DisplayName("signed with another secret → refused")
    void wrongSecret() throws Exception {
        assertEquals("INVALID_SIGNATURE", codeOf(BODY, "t=" + NOW + ",v1=" + sign("whsec_attacker", NOW, BODY)));
    }

    @Test
    @DisplayName("an old, real message sent again later (replay) → refused")
    void replayedOldMessage() throws Exception {
        long tenMinutesAgo = NOW - 600;
        assertEquals("WEBHOOK_TOO_OLD", codeOf(BODY, "t=" + tenMinutesAgo + ",v1=" + sign(SECRET, tenMinutesAgo, BODY)));
    }

    @Test
    @DisplayName("missing or broken header → refused")
    void brokenHeaders() {
        assertEquals("INVALID_SIGNATURE", codeOf(BODY, null));
        assertEquals("INVALID_SIGNATURE", codeOf(BODY, ""));
        assertEquals("INVALID_SIGNATURE", codeOf(BODY, "nonsense"));
        assertEquals("INVALID_SIGNATURE", codeOf(BODY, "t=" + NOW + ",v1=not-hex"));
    }
}
