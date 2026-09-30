package com.mycompany.gymbooking.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Creates random 6-digit codes like "048215".
 * SecureRandom is used instead of Random because codes must be hard to guess.
 */
@Component
public class VerificationCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        int number = random.nextInt(1_000_000);   // 0 .. 999999
        return String.format("%06d", number);     // pad with zeros: 48215 → "048215"
    }
}
