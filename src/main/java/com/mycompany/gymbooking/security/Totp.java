package com.mycompany.gymbooking.security;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords (RFC 6238): the 6-digit codes shown by authenticator apps such as
 * Google Authenticator or the iPhone's Passwords app. The app and the backend share a secret, and
 * both turn "the secret + the current 30-second step" into the same code, without any network.
 */
public final class Totp {

    public static final int STEP_SECONDS = 30;
    public static final int DIGITS = 6;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {
    }

    /** A new random secret: 20 bytes (the HMAC-SHA1 key size) in Base32, the format apps expect. */
    public static String newSecret(SecureRandom random) {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** Number of 30-second steps since 1970; the code changes when this does. */
    public static long stepAt(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), STEP_SECONDS);
    }

    /** The code an authenticator app shows during the given step. */
    public static String codeAt(String base32Secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(base32Secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            // The last 4 bits of the hash pick which 4 bytes become the code.
            int offset = hash[hash.length - 1] & 0x0f;
            int number = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", number % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is not available", e);
        }
    }

    /**
     * The step whose code matches, or empty. The steps just before and after are accepted too, in
     * case the phone's clock is a few seconds off.
     */
    public static OptionalLong matchingStep(String base32Secret, String code, Instant now) {
        long current = stepAt(now);
        for (long step = current - 1; step <= current + 1; step++) {
            // Compares in constant time, so response times don't reveal how many digits were right.
            if (MessageDigest.isEqual(codeAt(base32Secret, step).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    /** The otpauth:// link inside the QR code; authenticator apps read the secret and the names from it. */
    public static String otpauthUri(String issuer, String account, String base32Secret) {
        return "otpauth://totp/" + encode(issuer) + ":" + encode(account)
                + "?secret=" + base32Secret
                + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    static String base32Encode(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] base32Decode(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : text.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(Character.toUpperCase(c));
            if (value < 0) {
                throw new IllegalArgumentException("Not a Base32 character: " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
