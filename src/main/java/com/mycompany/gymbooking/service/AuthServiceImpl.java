package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AcceptInviteRequest;
import com.mycompany.gymbooking.dto.AuthResponse;
import com.mycompany.gymbooking.dto.ChangePasswordRequest;
import com.mycompany.gymbooking.dto.ForgotPasswordRequest;
import com.mycompany.gymbooking.dto.GoogleLoginRequest;
import com.mycompany.gymbooking.dto.LoginRequest;
import com.mycompany.gymbooking.dto.LoginResponse;
import com.mycompany.gymbooking.dto.MessageResponse;
import com.mycompany.gymbooking.dto.RecoveryCodesResponse;
import com.mycompany.gymbooking.dto.RefreshRequest;
import com.mycompany.gymbooking.dto.ResendCodeRequest;
import com.mycompany.gymbooking.dto.ResetPasswordRequest;
import com.mycompany.gymbooking.dto.SignUpRequest;
import com.mycompany.gymbooking.dto.TwoFactorChallengeRequest;
import com.mycompany.gymbooking.dto.TwoFactorCodeRequest;
import com.mycompany.gymbooking.dto.TwoFactorDisableRequest;
import com.mycompany.gymbooking.dto.TwoFactorLoginRequest;
import com.mycompany.gymbooking.dto.TwoFactorSetupRequest;
import com.mycompany.gymbooking.dto.TwoFactorSetupResponse;
import com.mycompany.gymbooking.dto.TwoFactorStep;
import com.mycompany.gymbooking.dto.VerifyEmailRequest;
import com.mycompany.gymbooking.exception.ApiException;
import com.mycompany.gymbooking.exception.BadRequestException;
import com.mycompany.gymbooking.exception.ConflictException;
import com.mycompany.gymbooking.exception.ForbiddenException;
import com.mycompany.gymbooking.exception.ServiceUnavailableException;
import com.mycompany.gymbooking.exception.TooManyRequestsException;
import com.mycompany.gymbooking.exception.UnauthorizedException;
import com.mycompany.gymbooking.model.Member;
import com.mycompany.gymbooking.model.Trainer;
import com.mycompany.gymbooking.model.User;
import com.mycompany.gymbooking.notification.NotificationSender;
import com.mycompany.gymbooking.notification.SecurityAlerts;
import com.mycompany.gymbooking.phone.PhoneNumbers;
import com.mycompany.gymbooking.repository.UserRepository;
import com.mycompany.gymbooking.security.ClientAddress;
import com.mycompany.gymbooking.security.GoogleIdTokenVerifier;
import com.mycompany.gymbooking.security.GoogleIdTokenVerifier.GoogleAccount;
import com.mycompany.gymbooking.security.LoginGuard;
import com.mycompany.gymbooking.security.RateLimiter;
import com.mycompany.gymbooking.security.TokenService;
import com.mycompany.gymbooking.service.TwoFactorService.CodeCheck;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-up, email verification, trainer invites, login (with a password or Google, and with or
 * without two-factor authentication) and password resets, with limits on code attempts, code resends,
 * emails and failed logins. Invites and
 * password resets use the same one-time code fields as email verification: an invite before the
 * account is verified, a reset only after.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    /** One bucket for the whole gym: emails anyone can trigger without logging in. */
    private static final String AUTH_EMAILS_KEY = "auth-emails";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationSender notificationSender;
    private final SecurityAlerts securityAlerts;
    private final SessionService sessionService;
    private final TwoFactorService twoFactorService;
    private final TokenService tokenService;
    private final GoogleIdTokenVerifier googleVerifier;
    private final VerificationCodeGenerator codeGenerator;
    private final Clock clock;
    private final long codeValidityMinutes;
    private final int maxCodeAttempts;
    private final long resendCooldownSeconds;
    private final int maxCodesPerDay;
    private final int authEmailsPerHour;
    private final LoginGuard loginGuard;
    private final RateLimiter rateLimiter;
    private final SecureRandom random = new SecureRandom();
    /** Checked against when the email is unknown, so a login takes as long either way. */
    private final String dummyPasswordHash;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           NotificationSender notificationSender,
                           SecurityAlerts securityAlerts,
                           SessionService sessionService,
                           TwoFactorService twoFactorService,
                           TokenService tokenService,
                           GoogleIdTokenVerifier googleVerifier,
                           VerificationCodeGenerator codeGenerator,
                           LoginGuard loginGuard,
                           RateLimiter rateLimiter,
                           Clock clock,
                           @Value("${app.verification.code-expiration-minutes}") long codeValidityMinutes,
                           @Value("${app.verification.max-attempts}") int maxCodeAttempts,
                           @Value("${app.verification.resend-cooldown-seconds}") long resendCooldownSeconds,
                           @Value("${app.verification.max-codes-per-day}") int maxCodesPerDay,
                           @Value("${app.mail.auth-emails-per-hour}") int authEmailsPerHour) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationSender = notificationSender;
        this.securityAlerts = securityAlerts;
        this.sessionService = sessionService;
        this.twoFactorService = twoFactorService;
        this.tokenService = tokenService;
        this.googleVerifier = googleVerifier;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
        this.codeValidityMinutes = codeValidityMinutes;
        this.maxCodeAttempts = maxCodeAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxCodesPerDay = maxCodesPerDay;
        this.authEmailsPerHour = authEmailsPerHour;
        this.loginGuard = loginGuard;
        this.rateLimiter = rateLimiter;
        this.dummyPasswordHash = unusablePasswordHash();
    }

    @Override
    @Transactional
    public MessageResponse signUp(SignUpRequest request) {
        String email = normalizeEmail(request.email());
        LocalDateTime now = LocalDateTime.now(clock);
        // Hashed in every case, so the time taken doesn't reveal whether the account exists.
        String passwordHash = passwordEncoder.encode(request.password());
        String fullName = request.fullName().trim();
        String phone = PhoneNumbers.toInternational(request.phone());

        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isEmpty()) {
            requireAuthEmailCapacity();
            Member member = new Member(fullName, email, phone, passwordHash);
            sendNewVerificationCode(member, now);
            userRepository.save(member);
        } else if (existing.get() instanceof Member member && !member.isVerified()) {
            // Never verified: the newest sign-up replaces it, so whoever proves they own the inbox
            // also chose the password.
            requireNewCodeAllowed(member, now);
            requireAuthEmailCapacity();
            member.replaceUnverifiedSignUp(fullName, phone, passwordHash);
            sendNewVerificationCode(member, now);
            userRepository.save(member);
        } else {
            // The account exists. Its owner is told by email, and the answer is the same as for a new
            // sign-up, so it doesn't reveal who has an account here.
            tellOwnerAboutSignUp(existing.get(), now);
        }
        return new MessageResponse("Check your email: we sent a message to " + email + ".");
    }

    @Override
    // Keep the wrong-code counter even though INVALID_CODE is thrown afterwards.
    @Transactional(noRollbackFor = BadRequestException.class)
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                // An invited trainer must set a password, so their code only works through acceptInvite.
                .filter(found -> !(found instanceof Trainer) || found.isVerified())
                .orElseThrow(() -> new BadRequestException("INVALID_CODE", "The code is not correct"));

        if (user.isVerified()) {
            throw new BadRequestException("ALREADY_VERIFIED", "This email is already verified. Please log in.");
        }
        // Checked before comparing the code so the correct code is refused too once attempts run out.
        if (user.hasNoCodeAttemptsLeft(maxCodeAttempts)) {
            throw new BadRequestException("TOO_MANY_ATTEMPTS", "Too many wrong codes. Request a new code.");
        }
        if (user.isVerificationCodeExpired(LocalDateTime.now(clock))) {
            throw new BadRequestException("CODE_EXPIRED", "The code has expired. Request a new one.");
        }
        if (!user.verificationCodeMatches(request.code())) {
            int triesLeft = user.recordWrongCode(maxCodeAttempts);
            userRepository.save(user);
            throw new BadRequestException("INVALID_CODE", triesLeft > 0
                    ? "The code is not correct. " + triesLeft + (triesLeft == 1 ? " try" : " tries") + " left."
                    : "The code is not correct. No tries left. Request a new code.");
        }
        // The code proves the inbox; the password proves this is the sign-up it was sent for, and not
        // one someone else made with this email.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordWrongCode(maxCodeAttempts);
            userRepository.save(user);
            throw new BadRequestException("SIGN_UP_REPLACED",
                    "This sign-up was replaced by a newer one with a different password. Sign up again to get a new code.");
        }

        user.markVerified();
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public MessageResponse resendCode(ResendCodeRequest request) {
        String email = normalizeEmail(request.email());

        // Same response whether or not the email exists, so this can't be used to probe for accounts.
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user instanceof Member && !user.isVerified())
                .ifPresent(user -> {
                    // Limits against inbox flooding. The errors do reveal a recent unverified sign-up.
                    LocalDateTime now = LocalDateTime.now(clock);
                    requireNewCodeAllowed(user, now);
                    requireAuthEmailCapacity();
                    sendNewVerificationCode(user, now);
                    userRepository.save(user);
                });

        return new MessageResponse("If this email needs verification, a new code has been sent.");
    }

    @Override
    // Keep the failed-login counter even though an error is thrown.
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest request) {
        Optional<User> found = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()));
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);   // same time as a real check
            throw invalidCredentials();
        }
        User user = found.get();
        LocalDateTime now = LocalDateTime.now(clock);
        String address = ClientAddress.current();

        // Checked before the password so the correct password is refused too while locked.
        Optional<LocalDateTime> lock = loginGuard.lockedUntil(user, address, now);
        if (lock.isPresent()) {
            throw accountLocked(lock.get(), now, "Too many wrong attempts.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean justLocked = loginGuard.recordWrong(user, address, now);
            userRepository.save(user);
            throw justLocked ? lockedNow(user, address, now, "Too many wrong passwords.") : invalidCredentials();
        }

        // With two-factor authentication the password is only the first step, so wrong attempts keep
        // counting until the code is right too. Otherwise the password could be entered again after
        // every few wrong codes to keep guessing.
        boolean codeStepFollows = user.isTwoFactorEnabled() || user.needsTwoFactorSetup();
        if (!codeStepFollows) {
            loginGuard.recordSuccess(user, address);
        }
        if (!user.isVerified()) {
            throw new ForbiddenException("EMAIL_NOT_VERIFIED", "Please verify your email first");
        }
        if (!user.isActive()) {
            throw new ForbiddenException("ACCOUNT_DEACTIVATED", "This account has been deactivated. Please contact the gym.");
        }

        return finishLogin(user);
    }

    @Override
    @Transactional
    public LoginResponse loginWithGoogle(GoogleLoginRequest request) {
        if (!googleVerifier.isEnabled()) {
            throw new ServiceUnavailableException("GOOGLE_SIGN_IN_OFF", "Google sign-in isn't set up on this server.");
        }
        GoogleAccount google = googleVerifier.verify(request.idToken())
                .orElseThrow(() -> new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google sign-in failed. Please try again."));

        User user = userRepository.findByGoogleSubject(google.subject())
                .orElseGet(() -> linkOrCreateMember(google));
        if (!user.isActive()) {
            throw new ForbiddenException("ACCOUNT_DEACTIVATED", "This account has been deactivated. Please contact the gym.");
        }
        // Google has proved who this is, so earlier wrong passwords no longer count, unless a code
        // from the authenticator app is still needed.
        if (!user.isTwoFactorEnabled()) {
            loginGuard.recordSuccess(user, ClientAddress.current());
        }
        userRepository.save(user);
        return finishLogin(user);
    }

    @Override
    // Keep the failed-attempt counter even though an error is thrown.
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse loginWithCode(TwoFactorLoginRequest request) {
        User user = challengedUser(request.challengeToken());
        if (!user.isTwoFactorEnabled()) {
            throw loginExpired();   // turned off on another device since the password step
        }
        requireSecondFactor(user, request.code());
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public TwoFactorSetupResponse startLoginSetup(TwoFactorChallengeRequest request) {
        User user = challengedUser(request.challengeToken());
        if (!user.needsTwoFactorSetup()) {
            throw loginExpired();
        }
        TwoFactorSetupResponse setup = twoFactorService.startSetup(user);
        userRepository.save(user);
        return setup;
    }

    @Override
    @Transactional
    public RecoveryCodesResponse confirmLoginSetup(TwoFactorLoginRequest request) {
        User user = challengedUser(request.challengeToken());
        if (!user.needsTwoFactorSetup()) {
            throw loginExpired();
        }
        List<String> recoveryCodes = twoFactorService.confirmSetup(user, request.code());
        loginGuard.recordSuccess(user, ClientAddress.current());
        // From now on every admin session has passed two-factor login, so any older ones end.
        user.endAllSessions();
        userRepository.save(user);
        securityAlerts.twoFactorOn(user);
        return new RecoveryCodesResponse(recoveryCodes, buildAuthResponse(user));
    }

    @Override
    // Keep the wrong-code counter even though INVALID_CODE is thrown afterwards.
    @Transactional(noRollbackFor = BadRequestException.class)
    public AuthResponse acceptInvite(AcceptInviteRequest request) {
        Trainer trainer = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(user -> user instanceof Trainer && !user.isVerified() && user.isActive())
                .map(Trainer.class::cast)
                .orElseThrow(() -> new BadRequestException("INVALID_CODE", "The email or code is not correct."));

        if (trainer.hasNoCodeAttemptsLeft(maxCodeAttempts)) {
            throw new BadRequestException("TOO_MANY_ATTEMPTS", "Too many wrong codes. Ask the gym to send a new invite.");
        }
        if (trainer.isVerificationCodeExpired(LocalDateTime.now(clock))) {
            throw new BadRequestException("CODE_EXPIRED", "This invite has expired. Ask the gym to send a new one.");
        }
        if (!trainer.verificationCodeMatches(request.code())) {
            int triesLeft = trainer.recordWrongCode(maxCodeAttempts);
            userRepository.save(trainer);
            throw new BadRequestException("INVALID_CODE", triesLeft > 0
                    ? "The email or code is not correct. " + triesLeft + (triesLeft == 1 ? " try" : " tries") + " left."
                    : "The email or code is not correct. No tries left. Ask the gym to send a new invite.");
        }

        trainer.changePasswordHash(passwordEncoder.encode(request.password()));
        trainer.markVerified();
        userRepository.save(trainer);

        return buildAuthResponse(trainer);
    }

    @Override
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                // Unverified members verify instead, and invited trainers accept their invite.
                .filter(user -> user.isVerified() && user.isActive())
                // Within the cooldown no new code is sent; the last one still works.
                .filter(user -> user.secondsUntilNewCodeAllowed(now, resendCooldownSeconds) == 0)
                // A few codes a day at most, or the 6 digits could be guessed over weeks of new codes.
                .filter(user -> !user.emailCodeLimitReached(now.toLocalDate(), maxCodesPerDay))
                .filter(user -> authEmailAllowed())
                .ifPresent(user -> {
                    String code = codeGenerator.generate();
                    user.issueVerificationCode(code, now, now.plusMinutes(codeValidityMinutes));
                    user.countEmailCode(now.toLocalDate());
                    userRepository.save(user);
                    notificationSender.send(
                            user.getEmail(),
                            "Reset your Gym Booking password",
                            "Hi " + user.getFullName() + ",\n\n"
                                    + "Your code to reset your password is: " + code + "\n"
                                    + "It expires in " + codeValidityMinutes + " minutes.\n\n"
                                    + "If you didn't ask for this, you can ignore this email. Your password stays the same.");
                });
        // Same answer whether or not the account exists, so this can't be used to find accounts.
        return new MessageResponse("If an account exists for this email, we sent it a code to reset the password.");
    }

    @Override
    // Keep the wrong-code counter even though INVALID_CODE is thrown afterwards.
    @Transactional(noRollbackFor = BadRequestException.class)
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .filter(found -> found.isVerified() && found.isActive() && found.hasVerificationCode())
                .orElseThrow(() -> new BadRequestException("INVALID_CODE", "The email or code is not correct."));

        if (user.hasNoCodeAttemptsLeft(maxCodeAttempts)) {
            throw new BadRequestException("TOO_MANY_ATTEMPTS", "Too many wrong codes. Request a new code.");
        }
        if (user.isVerificationCodeExpired(LocalDateTime.now(clock))) {
            throw new BadRequestException("CODE_EXPIRED", "The code has expired. Request a new one.");
        }
        if (!user.verificationCodeMatches(request.code())) {
            int triesLeft = user.recordWrongCode(maxCodeAttempts);
            userRepository.save(user);
            throw new BadRequestException("INVALID_CODE", triesLeft > 0
                    ? "The email or code is not correct. " + triesLeft + (triesLeft == 1 ? " try" : " tries") + " left."
                    : "The email or code is not correct. No tries left. Request a new code.");
        }

        boolean hadPassword = user.isPasswordSet();
        user.changePasswordHash(passwordEncoder.encode(request.password()));
        user.markVerified();            // clears the code so it can't be used again
        loginGuard.clearAll(user);      // proving the email also lifts the login locks
        user.endAllSessions();          // whoever knew the old password is logged out
        userRepository.save(user);
        securityAlerts.passwordReset(user, hadPassword);
        return new MessageResponse("Your password was changed. Log in with your new password.");
    }

    @Override
    public AuthResponse refresh(RefreshRequest request) {
        return sessionService.refresh(request.refreshToken());
    }

    @Override
    public void logout(RefreshRequest request) {
        sessionService.close(request.refreshToken());
    }

    @Override
    // Keep the failed-attempt counter even though WRONG_PASSWORD is thrown afterwards.
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        requirePassword(user, request.currentPassword(), "Your current password is not correct.");
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("SAME_PASSWORD", "Choose a password that's different from your current one.");
        }

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        loginGuard.recordSuccess(user, ClientAddress.current());
        user.endAllSessions();
        userRepository.save(user);
        securityAlerts.passwordChanged(user);
        return sessionService.open(user);   // this device stays logged in with a new session
    }

    @Override
    @Transactional
    public void logoutEverywhere(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        user.endAllSessions();
        userRepository.save(user);
    }

    @Override
    // Keep the failed-attempt counter even though an error is thrown.
    @Transactional(noRollbackFor = ApiException.class)
    public TwoFactorSetupResponse startTwoFactorSetup(Long userId, TwoFactorSetupRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        // The password is asked for so that someone using an unlocked phone can't lock the owner out
        // by connecting their own authenticator app.
        requirePassword(user, request.password(), "Your password is not correct.");
        if (user.isTwoFactorEnabled()) {
            // Moving to a new phone: the current app (or a recovery code) must agree too.
            if (request.code() == null || request.code().isBlank()) {
                throw new BadRequestException("TWO_FACTOR_CODE_REQUIRED",
                        "Enter a code from your current authenticator app, or a recovery code.");
            }
            requireSecondFactor(user, request.code());
        }
        loginGuard.recordSuccess(user, ClientAddress.current());
        TwoFactorSetupResponse setup = twoFactorService.startSetup(user);
        userRepository.save(user);
        return setup;
    }

    @Override
    @Transactional
    public RecoveryCodesResponse confirmTwoFactorSetup(Long userId, TwoFactorCodeRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        boolean movingToNewPhone = user.isTwoFactorEnabled();
        List<String> recoveryCodes = twoFactorService.confirmSetup(user, request.code());
        userRepository.save(user);
        if (movingToNewPhone) {
            securityAlerts.twoFactorMoved(user);
        } else {
            securityAlerts.twoFactorOn(user);
        }
        return new RecoveryCodesResponse(recoveryCodes, null);   // other devices stay logged in
    }

    @Override
    // Keep the failed-attempt counter even though an error is thrown.
    @Transactional(noRollbackFor = ApiException.class)
    public void disableTwoFactor(Long userId, TwoFactorDisableRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        if (user.requiresTwoFactor()) {
            throw new ForbiddenException("TWO_FACTOR_REQUIRED", "Admins must keep two-factor authentication on.");
        }
        if (!user.isTwoFactorEnabled()) {
            throw new BadRequestException("TWO_FACTOR_OFF", "Two-factor authentication is already off.");
        }
        requirePassword(user, request.password(), "Your password is not correct.");
        requireSecondFactor(user, request.code());
        user.disableTwoFactor();
        userRepository.save(user);
        securityAlerts.twoFactorOff(user);
    }

    /**
     * Emails a new verification code. The name isn't in it: before verification it was typed by
     * whoever signed up, who may not own this inbox.
     */
    private void sendNewVerificationCode(User user, LocalDateTime now) {
        String code = codeGenerator.generate();
        user.issueVerificationCode(code, now, now.plusMinutes(codeValidityMinutes));
        user.countEmailCode(now.toLocalDate());

        notificationSender.send(
                user.getEmail(),
                "Your Gym Booking verification code",
                "Hello,\n\n"
                        + "Your verification code is: " + code + "\n"
                        + "It expires in " + codeValidityMinutes + " minutes.\n\n"
                        + "If you didn't sign up, you can ignore this email.");
    }

    /** Emails the owner of an existing account that someone tried to sign up with their address. */
    private void tellOwnerAboutSignUp(User user, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (user.emailCodeLimitReached(today, maxCodesPerDay) || !authEmailAllowed()) {
            return;
        }
        user.countEmailCode(today);
        userRepository.save(user);
        notificationSender.send(
                user.getEmail(),
                "Someone tried to sign up with your email",
                "Hello,\n\n"
                        + "Someone just tried to create a Gym Booking account with this email address, but you already have one.\n\n"
                        + "If it was you, log in instead. If you forgot your password, use \"Forgot password?\" on the login screen.\n\n"
                        + "If it wasn't you, you can ignore this email. Nothing about your account has changed.");
    }

    /** Throws if this address must wait for its next code, or already got today's maximum. */
    private void requireNewCodeAllowed(User user, LocalDateTime now) {
        long wait = user.secondsUntilNewCodeAllowed(now, resendCooldownSeconds);
        if (wait > 0) {
            throw new TooManyRequestsException("RESEND_TOO_SOON",
                    "Please wait " + TooManyRequestsException.waitText(wait) + " before asking for a new code.", wait);
        }
        LocalDate today = now.toLocalDate();
        if (user.emailCodeLimitReached(today, maxCodesPerDay)) {
            long untilTomorrow = Duration.between(now, today.plusDays(1).atStartOfDay()).toSeconds();
            throw new TooManyRequestsException("TOO_MANY_CODES",
                    "You've asked for " + maxCodesPerDay + " codes today. Please try again tomorrow.", untilTomorrow);
        }
    }

    /** Throws when the gym-wide limit on emails sent without logging in is reached for now. */
    private void requireAuthEmailCapacity() {
        if (!authEmailAllowed()) {
            throw new TooManyRequestsException("EMAIL_BUSY",
                    "We're sending a lot of emails right now. Please try again in a few minutes.", 300);
        }
    }

    /** Takes one email from the gym-wide hourly allowance, if any is left. */
    private boolean authEmailAllowed() {
        boolean allowed = rateLimiter.tryConsume(AUTH_EMAILS_KEY, authEmailsPerHour, Duration.ofHours(1)).allowed();
        if (!allowed) {
            log.warn("The hourly limit of {} sign-up and password emails is reached", authEmailsPerHour);
        }
        return allowed;
    }

    private AuthResponse buildAuthResponse(User user) {
        return sessionService.open(user);
    }

    /** After the first step (password or Google): a session, or the two-factor step. */
    private LoginResponse finishLogin(User user) {
        if (user.isTwoFactorEnabled()) {
            return LoginResponse.nextStep(TwoFactorStep.CODE_REQUIRED, tokenService.generateLoginChallenge(user));
        }
        if (user.needsTwoFactorSetup()) {
            return LoginResponse.nextStep(TwoFactorStep.SETUP_REQUIRED, tokenService.generateLoginChallenge(user));
        }
        return LoginResponse.loggedIn(buildAuthResponse(user));
    }

    /** A first Google sign-in: links the member account with the same email, or creates one. */
    private User linkOrCreateMember(GoogleAccount google) {
        String email = normalizeEmail(google.email());
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing == null) {
            if (!google.emailVerified()) {
                throw new UnauthorizedException("GOOGLE_EMAIL_NOT_VERIFIED",
                        "Your Google account's email isn't verified. Verify it with Google, or sign up with a password.");
            }
            String name = google.name() == null || google.name().isBlank() ? email.substring(0, email.indexOf('@')) : google.name().trim();
            Member member = Member.signedUpWithGoogle(name.length() > 100 ? name.substring(0, 100) : name,
                    email, google.subject(), unusablePasswordHash());
            return userRepository.save(member);
        }

        if (!(existing instanceof Member)) {
            throw new ForbiddenException("GOOGLE_MEMBERS_ONLY",
                    "Google sign-in is for members. Trainers and admins log in with their password.");
        }
        // Linking gives this Google account access to the existing account, so Google must be the
        // one that controls the email address, not just someone who typed it in.
        if (!google.googleOwnsEmail()) {
            throw new ConflictException("ACCOUNT_EXISTS",
                    "An account with this email already exists. Log in with your password.");
        }
        if (existing.isVerified()) {
            securityAlerts.googleLinked(existing);
        } else {
            // Whoever signed up with this email never proved they own it; Google just did. Their
            // password is removed so they can't get in.
            existing.removePassword(unusablePasswordHash());
            existing.markVerified();
        }
        existing.linkGoogle(google.subject());
        return existing;
    }

    /** The hash of a random password nobody knows, for accounts without a password. */
    private String unusablePasswordHash() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return passwordEncoder.encode(Base64.getEncoder().encodeToString(bytes));
    }

    /** Wrong passwords count like failed logins, so a logged-in phone can't be used to guess the password. */
    private void requirePassword(User user, String password, String wrongPasswordMessage) {
        LocalDateTime now = LocalDateTime.now(clock);
        String address = ClientAddress.current();
        Optional<LocalDateTime> lock = loginGuard.lockedUntil(user, address, now);
        if (lock.isPresent()) {
            throw accountLocked(lock.get(), now, "Too many wrong attempts.");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            boolean justLocked = loginGuard.recordWrong(user, address, now);
            userRepository.save(user);
            throw justLocked ? lockedNow(user, address, now, "Too many wrong passwords.")
                    : new BadRequestException("WRONG_PASSWORD", wrongPasswordMessage);
        }
    }

    /**
     * Checks a code from the authenticator app or a recovery code. Wrong codes count like wrong
     * passwords, so the 1-in-a-million chance per guess can't be repeated often enough to matter.
     */
    private void requireSecondFactor(User user, String code) {
        LocalDateTime now = LocalDateTime.now(clock);
        String address = ClientAddress.current();
        Optional<LocalDateTime> lock = loginGuard.lockedUntil(user, address, now);
        if (lock.isPresent()) {
            throw accountLocked(lock.get(), now, "Too many wrong attempts.");
        }
        CodeCheck result = twoFactorService.check(user, code);
        if (result == CodeCheck.ACCEPTED) {
            loginGuard.recordSuccess(user, address);
            return;
        }
        boolean justLocked = loginGuard.recordWrong(user, address, now);
        userRepository.save(user);
        if (justLocked) {
            throw lockedNow(user, address, now, "Too many wrong codes.");
        }
        throw result == CodeCheck.ALREADY_USED
                ? new BadRequestException("CODE_ALREADY_USED",
                        "This code was already used. Wait for the next code in your authenticator app.")
                : new BadRequestException("INVALID_TWO_FACTOR_CODE",
                        "The code is not correct. Enter the current code from your authenticator app, or a recovery code.");
    }

    /** The user who passed the password step, if their challenge token is still valid. */
    private User challengedUser(String challengeToken) {
        return tokenService.readLoginChallenge(challengeToken)
                .flatMap(challenge -> userRepository.findByEmailIgnoreCase(challenge.email())
                        // A newer version means the password was changed or the sessions were ended since.
                        .filter(user -> user.getTokenVersion() == challenge.version()))
                .filter(user -> user.isVerified() && user.isActive())
                .orElseThrow(this::loginExpired);
    }

    private UnauthorizedException loginExpired() {
        return new UnauthorizedException("LOGIN_EXPIRED", "Your login has expired. Please log in again.");
    }

    /** For an attempt that just started a lock. */
    private TooManyRequestsException lockedNow(User user, String address, LocalDateTime now, String reason) {
        return accountLocked(loginGuard.lockedUntil(user, address, now).orElse(now), now, reason);
    }

    private TooManyRequestsException accountLocked(LocalDateTime until, LocalDateTime now, String reason) {
        long seconds = Math.max(1, Duration.between(now, until).toSeconds());
        return new TooManyRequestsException("ACCOUNT_LOCKED",
                reason + " Try again in " + TooManyRequestsException.waitText(seconds) + ".",
                seconds);
    }

    private UnauthorizedException invalidCredentials() {
        // Same error for unknown email and wrong password, so accounts can't be enumerated.
        return new UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
