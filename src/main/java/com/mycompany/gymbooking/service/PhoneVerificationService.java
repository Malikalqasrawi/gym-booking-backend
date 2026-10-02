package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.PhoneCodeSentResponse;
import com.mycompany.gymbooking.dto.UserResponse;
import com.mycompany.gymbooking.exception.ApiException;
import com.mycompany.gymbooking.exception.BadRequestException;
import com.mycompany.gymbooking.exception.ConflictException;
import com.mycompany.gymbooking.exception.TooManyRequestsException;
import com.mycompany.gymbooking.model.PhoneVerification;
import com.mycompany.gymbooking.model.User;
import com.mycompany.gymbooking.phone.PhoneCodes;
import com.mycompany.gymbooking.repository.PhoneVerificationRepository;
import com.mycompany.gymbooking.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirms that users own their phone number: a code is texted to it, and entering the code marks
 * the number as verified. Members do this before their first booking. Every text message costs
 * money, so codes are limited: one a minute and a few a day per account, with 5 tries each.
 */
@Service
public class PhoneVerificationService {

    private final UserRepository userRepository;
    private final PhoneVerificationRepository verifications;
    private final PhoneCodes phoneCodes;
    private final Clock clock;
    private final long codeValidityMinutes;
    private final int maxAttempts;
    private final long resendCooldownSeconds;
    private final int maxCodesPerDay;

    public PhoneVerificationService(UserRepository userRepository,
                                    PhoneVerificationRepository verifications,
                                    PhoneCodes phoneCodes,
                                    Clock clock,
                                    @Value("${app.phone.code-expiration-minutes}") long codeValidityMinutes,
                                    @Value("${app.phone.max-attempts}") int maxAttempts,
                                    @Value("${app.phone.resend-cooldown-seconds}") long resendCooldownSeconds,
                                    @Value("${app.phone.max-codes-per-day}") int maxCodesPerDay) {
        this.userRepository = userRepository;
        this.verifications = verifications;
        this.phoneCodes = phoneCodes;
        this.clock = clock;
        this.codeValidityMinutes = codeValidityMinutes;
        this.maxAttempts = maxAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxCodesPerDay = maxCodesPerDay;
    }

    /** Texts a new code to the user's phone number. */
    @Transactional
    public PhoneCodeSentResponse sendCode(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new BadRequestException("PHONE_REQUIRED", "Add your phone number first.");
        }
        if (user.isPhoneVerified()) {
            throw new ConflictException("PHONE_ALREADY_VERIFIED", "Your phone number is already confirmed.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        PhoneVerification verification = verifications.findById(userId).orElseGet(() -> new PhoneVerification(userId));
        long wait = verification.secondsUntilResend(user.getPhone(), now, resendCooldownSeconds);
        if (wait > 0) {
            throw new TooManyRequestsException("RESEND_TOO_SOON",
                    "Please wait " + TooManyRequestsException.waitText(wait) + " before asking for a new code.", wait);
        }
        if (verification.dailyLimitReached(now.toLocalDate(), maxCodesPerDay)) {
            long untilTomorrow = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toSeconds();
            throw new TooManyRequestsException("TOO_MANY_CODES",
                    "You've asked for " + maxCodesPerDay + " codes today. Please try again tomorrow.", untilTomorrow);
        }

        // Sent first: if the SMS fails, nothing is recorded and the user can try again right away.
        phoneCodes.send(user.getPhone());
        verification.codeSent(user.getPhone(), now, now.plusMinutes(codeValidityMinutes));
        verifications.save(verification);
        return new PhoneCodeSentResponse(user.getPhone(), resendCooldownSeconds, codeValidityMinutes);
    }

    /** Checks the code; a correct one confirms the phone number. */
    @Transactional(noRollbackFor = ApiException.class)   // keep the wrong-try count
    public UserResponse confirm(Long userId, String code) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.isPhoneVerified()) {
            return UserResponse.from(user);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        PhoneVerification verification = verifications.findById(userId).orElse(null);
        if (verification == null || !verification.hasUsableCode(user.getPhone(), now)) {
            throw codeExpired();
        }
        if (verification.hasNoAttemptsLeft(maxAttempts)) {
            throw new BadRequestException("TOO_MANY_ATTEMPTS", "Too many wrong codes. Ask for a new code.");
        }

        switch (phoneCodes.check(user.getPhone(), code)) {
            case CORRECT -> {
                verification.endCode();
                user.markPhoneVerified(now);
                return UserResponse.from(userRepository.save(user));
            }
            case WRONG -> {
                int triesLeft = verification.recordWrongCode(maxAttempts);
                throw new BadRequestException("INVALID_CODE", triesLeft > 0
                        ? "The code is not correct. " + triesLeft + (triesLeft == 1 ? " try" : " tries") + " left."
                        : "The code is not correct. No tries left. Ask for a new code.");
            }
            default -> {
                verification.endCode();
                throw codeExpired();
            }
        }
    }

    private static BadRequestException codeExpired() {
        return new BadRequestException("CODE_EXPIRED", "This code has expired. Ask for a new one.");
    }
}
