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

/**
 * HOW authentication works. This is the "kitchen": all the rules live here.
 *
 * DEPENDENCY INJECTION: we don't write "new UserRepository()" or "new EmailSender()".
 * We ask for them in the constructor and Spring hands us the right objects.
 * Every dependency is an INTERFACE (UserRepository, PasswordEncoder, NotificationSender, TokenService),
 * so this class doesn't care whether emails go to Gmail or the console, or whether tokens are JWT.
 */
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

    // ------------------------------------------------------------------
    // SIGN UP
    // ------------------------------------------------------------------
    @Override
    @Transactional
    public MessageResponse signUp(SignUpRequest request) {
        String email = normalizeEmail(request.email());

        // Rule 1: one account per email
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }

        // Rule 2: only members can sign up themselves (trainers/admins are created by the admin)
        String passwordHash = passwordEncoder.encode(request.password());
        Member member = new Member(request.fullName().trim(), email, request.phone().trim(), passwordHash);

        sendNewVerificationCode(member);
        userRepository.save(member);

        return new MessageResponse("Account created. We sent a 6-digit code to " + email);
    }

    // ------------------------------------------------------------------
    // VERIFY EMAIL
    // ------------------------------------------------------------------
    @Override
    // noRollbackFor: normally an exception cancels (rolls back) every change in this method.
    // We WANT the wrong-code counter to be saved even though we throw INVALID_CODE afterwards.
    @Transactional(noRollbackFor = BadRequestException.class)
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new BadRequestException("INVALID_CODE", "The code is not correct"));

        if (user.isVerified()) {
            throw new BadRequestException("ALREADY_VERIFIED", "This email is already verified. Please log in.");
        }
        // Checked BEFORE comparing the code: once locked, even the right code is refused.
        // This stops someone from guessing 000000, 000001, 000002... until one works.
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

        // Log them in straight away, so they don't have to type the password again
        return buildAuthResponse(user);
    }

    // ------------------------------------------------------------------
    // RESEND CODE
    // ------------------------------------------------------------------
    @Override
    @Transactional
    public MessageResponse resendCode(ResendCodeRequest request) {
        String email = normalizeEmail(request.email());

        // We give the same answer whether or not the email exists,
        // so nobody can use this endpoint to discover who has an account.
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !user.isVerified())
                .ifPresent(user -> {
                    // At most one code per minute per account: stops someone flooding a person's inbox.
                    // (This tells the caller that an unverified account exists, but sign-up already
                    // reveals that with EMAIL_TAKEN, so nothing new is given away.)
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

    // ------------------------------------------------------------------
    // LOGIN
    // ------------------------------------------------------------------
    @Override
    // noRollbackFor: keep the wrong-password counter even though we answer with an error
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(this::invalidCredentials);
        LocalDateTime now = LocalDateTime.now();

        // Checked BEFORE the password: while locked, even the right password is refused,
        // so guessing is pointless for 15 minutes.
        if (user.isLoginLocked(now)) {
            throw accountLocked(user, now);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean justLocked = user.recordFailedLogin(maxLoginAttempts, now.plusMinutes(loginLockMinutes));
            userRepository.save(user);
            throw justLocked ? accountLocked(user, now) : invalidCredentials();
        }

        user.recordSuccessfulLogin();   // correct password: the wrong-password count starts again from 0
        if (!user.isVerified()) {
            // The app checks this code and opens the "enter code" screen
            throw new ForbiddenException("EMAIL_NOT_VERIFIED", "Please verify your email first");
        }

        return buildAuthResponse(user);
    }

    // ------------------------------------------------------------------
    // Small private helpers (not part of the interface)
    // ------------------------------------------------------------------

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
        // Same message for "no such email" and "wrong password" (don't reveal which one was wrong)
        return new UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
