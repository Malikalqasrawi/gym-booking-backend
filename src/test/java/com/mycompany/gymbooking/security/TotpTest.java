package com.mycompany.gymbooking.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Authenticator-app codes, checked against the test values published in RFC 6238. */
class TotpTest {

    /** The RFC's SHA-1 test key, the ASCII text "12345678901234567890", in Base32. */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";   // gitleaks:allow (public test key)

    @Test
    @DisplayName("codes match the RFC 6238 test values (last 6 of their 8 digits)")
    void rfcTestValues() {
        assertEquals("287082", codeAt(59));
        assertEquals("081804", codeAt(1111111109));
        assertEquals("050471", codeAt(1111111111));
        assertEquals("005924", codeAt(1234567890));
        assertEquals("279037", codeAt(2000000000));
        assertEquals("353130", codeAt(20000000000L));
    }

    @Test
    @DisplayName("Base32 works both ways, and new secrets are 32 characters (20 bytes)")
    void base32() {
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertEquals(RFC_SECRET, Totp.base32Encode(key));
        assertArrayEquals(key, Totp.base32Decode(RFC_SECRET));
        assertArrayEquals(key, Totp.base32Decode(RFC_SECRET.toLowerCase()), "lower case is accepted too");

        String secret = Totp.newSecret(new SecureRandom());
        assertEquals(32, secret.length());
        assertEquals(20, Totp.base32Decode(secret).length);
    }

    @Test
    @DisplayName("the steps before and after are accepted, older ones aren't")
    void clockDrift() {
        Instant now = Instant.ofEpochSecond(1111111111);
        long step = Totp.stepAt(now);
        assertEquals(OptionalLong.of(step - 1), Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, step - 1), now));
        assertEquals(OptionalLong.of(step + 1), Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, step + 1), now));
        assertTrue(Totp.matchingStep(RFC_SECRET, Totp.codeAt(RFC_SECRET, step - 2), now).isEmpty());
        assertTrue(Totp.matchingStep(RFC_SECRET, "12345", now).isEmpty());
    }

    @Test
    @DisplayName("the QR code link names the gym and the account")
    void otpauthUri() {
        assertEquals("otpauth://totp/Gym%20Booking:admin%40gym.com?secret=" + RFC_SECRET
                        + "&issuer=Gym%20Booking&algorithm=SHA1&digits=6&period=30",
                Totp.otpauthUri("Gym Booking", "admin@gym.com", RFC_SECRET));
    }

    private static String codeAt(long epochSecond) {
        return Totp.codeAt(RFC_SECRET, Totp.stepAt(Instant.ofEpochSecond(epochSecond)));
    }
}
