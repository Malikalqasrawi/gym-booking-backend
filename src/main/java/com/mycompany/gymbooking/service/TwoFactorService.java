package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.TwoFactorSetupResponse;
import com.mycompany.gymbooking.exception.BadRequestException;
import com.mycompany.gymbooking.model.User;
import com.mycompany.gymbooking.security.Sha256;
import com.mycompany.gymbooking.security.Totp;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;

/**
 * Two-factor login with an authenticator app: setting it up, recovery codes, and checking codes.
 * Limits on wrong codes are applied by the callers in AuthServiceImpl, like for passwords.
 */
@Service
public class TwoFactorService {

    /** The name the authenticator app shows above the codes. */
    private static final String ISSUER = "Gym Booking";
    private static final int RECOVERY_CODE_COUNT = 8;
    /** No 0/o, 1/i/l, which are easy to mix up when typing a code from paper. */
    private static final String RECOVERY_CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    /** The result of checking a code. */
    public enum CodeCheck {
        ACCEPTED,
        WRONG,
        /** Right code, but it was already used; the app shows a new one within 30 seconds. */
        ALREADY_USED
    }

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(Clock clock) {
        this.clock = clock;
    }

    /** Creates a new secret for the user's authenticator app. Two-factor login stays as it is until confirmed. */
    public TwoFactorSetupResponse startSetup(User user) {
        String secret = Totp.newSecret(random);
        user.startTwoFactorSetup(secret);
        return new TwoFactorSetupResponse(secret, Totp.otpauthUri(ISSUER, user.getEmail(), secret));
    }

    /**
     * Turns two-factor login on once a code shows the app has the new secret, and returns new
     * recovery codes. Any old secret and recovery codes stop working.
     */
    public List<String> confirmSetup(User user, String code) {
        String pending = user.getPendingTwoFactorSecret();
        if (pending == null) {
            throw new BadRequestException("TWO_FACTOR_SETUP_NOT_STARTED", "Start the setup again to get a new QR code.");
        }
        OptionalLong step = Totp.matchingStep(pending, normalize(code), clock.instant());
        if (step.isEmpty()) {
            throw new BadRequestException("INVALID_TWO_FACTOR_CODE",
                    "The code is not correct. Make sure you scanned the QR code on this screen and try again.");
        }

        List<String> codes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String recoveryCode = newRecoveryCode();
            codes.add(recoveryCode.substring(0, 5) + "-" + recoveryCode.substring(5));
            hashes.add(Sha256.hex(recoveryCode));
        }
        user.enableTwoFactor(step.getAsLong(), hashes);
        return codes;
    }

    /**
     * Checks a 6-digit code from the authenticator app, or a recovery code (which is then used up).
     * Spaces and dashes are ignored.
     */
    public CodeCheck check(User user, String code) {
        String normalized = normalize(code);
        if (normalized.matches("[0-9]{" + Totp.DIGITS + "}")) {
            OptionalLong step = Totp.matchingStep(user.getTwoFactorSecret(), normalized, clock.instant());
            if (step.isEmpty()) {
                return CodeCheck.WRONG;
            }
            if (user.isTwoFactorStepUsed(step.getAsLong())) {
                return CodeCheck.ALREADY_USED;   // e.g. a code someone saw over the user's shoulder
            }
            user.recordTwoFactorStep(step.getAsLong());
            return CodeCheck.ACCEPTED;
        }
        return user.useRecoveryCode(Sha256.hex(normalized)) ? CodeCheck.ACCEPTED : CodeCheck.WRONG;
    }

    private String newRecoveryCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            code.append(RECOVERY_CODE_ALPHABET.charAt(random.nextInt(RECOVERY_CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private static String normalize(String code) {
        return code.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
    }
}
