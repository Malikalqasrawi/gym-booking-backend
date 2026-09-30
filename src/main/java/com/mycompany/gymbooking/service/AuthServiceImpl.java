package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AuthResponse;
import com.mycompany.gymbooking.dto.LoginRequest;
import com.mycompany.gymbooking.dto.MessageResponse;
import com.mycompany.gymbooking.dto.ResendCodeRequest;
import com.mycompany.gymbooking.dto.SignUpRequest;
import com.mycompany.gymbooking.dto.UserResponse;
import com.mycompany.gymbooking.dto.VerifyEmailRequest;
import com.mycompany.gymbooking.exception.ApiException;
import com.mycompany.gymbooking.exception.BadRequestException;
import com.mycompany.gymbooking.exception.ConflictException;
import com.mycompany.gymbooking.exception.ForbiddenException;
import com.mycompany.gymbooking.exception.TooManyRequestsException;
import com.mycompany.gymbooking.exception.UnauthorizedException;
import com.mycompany.gymbooking.model.Member;
import com.mycompany.gymbooking.model.User;
import com.mycompany.gymbooking.notification.NotificationSender;
import com.mycompany.gymbooking.repository.UserRepository;
import com.mycompany.gymbooking.security.TokenService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-up, email verification and login, with limits on code attempts, code resends and failed logins. */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationSender notificationSender;
    private final TokenService tokenService;
    private final VerificationCodeGenerator codeGenerator;
    private final long codeValidityMinutes;
    private final int maxCodeAttempts;
    private final long resendCooldownSeconds;
    private final int maxLoginAttempts;
    private final long loginLockMinutes;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           NotificationSender notificationSender,
                           TokenService tokenService,
                           VerificationCodeGenerator codeGenerator,
                           @Value("${app.verification.code-expiration-minutes}") long codeValidityMinutes,
                           @Value("${app.verification.max-attempts}") int maxCodeAttempts,
                           @Value("${app.verification.resend-cooldown-seconds}") long resendCooldownSeconds,
                           @Value("${app.login.max-attempts}") int maxLoginAttempts,
                           @Value("${app.login.lock-minutes}") long loginLockMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationSender = notificationSender;
        this.tokenService = tokenService;
        this.codeGenerator = codeGenerator;
        this.codeValidityMinutes = codeValidityMinutes;
        this.maxCodeAttempts = maxCodeAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxLoginAttempts = maxLoginAttempts;
        this.loginLockMinutes = loginLockMinutes;
    }

    @Override
    @Transactional
    public MessageResponse signUp(SignUpRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        Member member = new Member(request.fullName().trim(), email, request.phone().trim(), passwordHash);

        sendNewVerificationCode(member);
        userRepository.save(member);

        return new MessageResponse("Account created. We sent a 6-digit code to " + email);
    }

    @Override
    // Keep the wrong-code counter even though INVALID_CODE is thrown afterwards.
    @Transactional(noRollbackFor = BadRequestException.class)
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new BadRequestException("INVALID_CODE", "The code is not correct"));

        if (user.isVerified()) {
            throw new BadRequestException("ALREADY_VERIFIED", "This email is already verified. Please log in.");
        }
        // Checked before comparing the code so the correct code is refused too once attempts run out.
        if (user.hasNoCodeAttemptsLeft(maxCodeAttempts)) {
            throw new BadRequestException("TOO_MANY_ATTEMPTS", "Too many wrong codes. Request a new code.");
        }
        if (user.isVerificationCodeExpired(LocalDateTime.now())) {
            throw new BadRequestException("CODE_EXPIRED", "The code has expired. Request a new one.");
        }
        if (!user.verificationCodeMatches(request.code())) {
            int triesLeft = user.recordWrongCode(maxCodeAttempts);
            userRepository.save(user);
            throw new BadRequestException("INVALID_CODE", triesLeft > 0
                    ? "The code is not correct. " + triesLeft + (triesLeft == 1 ? " try" : " tries") + " left."
                    : "The code is not correct. No tries left. Request a new code.");
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
                .filter(user -> !user.isVerified())
                .ifPresent(user -> {
                    // Per-account cooldown against inbox flooding. The error does reveal an unverified
                    // account, but sign-up already reveals that via EMAIL_TAKEN.
                    long wait = user.secondsUntilNewCodeAllowed(LocalDateTime.now(), resendCooldownSeconds);
                    if (wait > 0) {
                        throw new TooManyRequestsException("RESEND_TOO_SOON",
                                "Please wait " + TooManyRequestsException.waitText(wait) + " before asking for a new code.",
                                wait);
                    }
                    sendNewVerificationCode(user);
                    userRepository.save(user);
                });

        return new MessageResponse("If this email needs verification, a new code has been sent.");
    }

    @Override
    // Keep the failed-login counter even though an error is thrown.
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(this::invalidCredentials);
        LocalDateTime now = LocalDateTime.now();

        // Checked before the password so the correct password is refused too while locked.
        if (user.isLoginLocked(now)) {
            throw accountLocked(user, now);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean justLocked = user.recordFailedLogin(maxLoginAttempts, now.plusMinutes(loginLockMinutes));
            userRepository.save(user);
            throw justLocked ? accountLocked(user, now) : invalidCredentials();
        }

        user.recordSuccessfulLogin();
        if (!user.isVerified()) {
            throw new ForbiddenException("EMAIL_NOT_VERIFIED", "Please verify your email first");
        }

        return buildAuthResponse(user);
    }

    private void sendNewVerificationCode(User user) {
        String code = codeGenerator.generate();
        LocalDateTime now = LocalDateTime.now();
        user.issueVerificationCode(code, now, now.plusMinutes(codeValidityMinutes));

        notificationSender.send(
                user.getEmail(),
                "Your Gym Booking verification code",
                "Hi " + user.getFullName() + ",\n\n"
                        + "Your verification code is: " + code + "\n"
                        + "It expires in " + codeValidityMinutes + " minutes.\n\n"
                        + "If you didn't sign up, you can ignore this email.");
    }

    private AuthResponse buildAuthResponse(User user) {
        return new AuthResponse(tokenService.generateToken(user), UserResponse.from(user));
    }

    private TooManyRequestsException accountLocked(User user, LocalDateTime now) {
        long seconds = Math.max(1, Duration.between(now, user.getLoginLockedUntil()).toSeconds());
        return new TooManyRequestsException("ACCOUNT_LOCKED",
                "Too many wrong passwords. Try again in " + TooManyRequestsException.waitText(seconds) + ".",
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
