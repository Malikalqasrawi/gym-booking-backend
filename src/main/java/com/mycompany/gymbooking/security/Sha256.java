package com.mycompany.gymbooking.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes long random values such as refresh tokens and recovery codes before they are stored. A
 * fast hash is enough for these, unlike passwords: they are random, so they can't be guessed.
 */
public final class Sha256 {

    private Sha256() {
    }

    public static String hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
