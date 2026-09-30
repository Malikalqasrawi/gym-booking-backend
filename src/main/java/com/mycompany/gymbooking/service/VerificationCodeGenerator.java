package com.mycompany.gymbooking.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Generates zero-padded 6-digit verification codes from a SecureRandom so they can't be predicted. */
@Component
public class VerificationCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        int number = random.nextInt(1_000_000);
        return String.format("%06d", number);
    }
}
