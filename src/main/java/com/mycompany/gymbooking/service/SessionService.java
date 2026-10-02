package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AuthResponse;
import com.mycompany.gymbooking.dto.UserResponse;
import com.mycompany.gymbooking.exception.UnauthorizedException;
import com.mycompany.gymbooking.model.RefreshToken;
import com.mycompany.gymbooking.model.User;
import com.mycompany.gymbooking.repository.RefreshTokenRepository;
import com.mycompany.gymbooking.security.Sha256;
import com.mycompany.gymbooking.security.TokenService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login sessions: a 15-minute access token plus a refresh token that the app trades for a new pair.
 * Refresh tokens rotate on every use and expire after a set number of days without use.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    /** A token reused this soon after rotating is most likely a retried request, not a stolen copy. */
    private static final Duration RETRY_GRACE = Duration.ofMinutes(1);

    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokenService;
    private final Clock clock;
    private final long refreshTokenDays;
    private final SecureRandom random = new SecureRandom();

    public SessionService(RefreshTokenRepository refreshTokens,
                          TokenService tokenService,
                          Clock clock,
                          @Value("${app.auth.refresh-token-days}") long refreshTokenDays) {
        this.refreshTokens = refreshTokens;
        this.tokenService = tokenService;
        this.clock = clock;
        this.refreshTokenDays = refreshTokenDays;
    }

    /** Starts a session on one device. */
    @Transactional
    public AuthResponse open(User user) {
        LocalDateTime now = LocalDateTime.now(clock);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user, Sha256.hex(rawToken), now, now.plusDays(refreshTokenDays)));
        return new AuthResponse(tokenService.generateToken(user), rawToken, UserResponse.from(user));
    }

    /**
     * Trades a refresh token for a new pair and retires the old one. If a retired token comes back
     * later, someone may have copied it, so all of the user's sessions are ended.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse refresh(String rawToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken token = refreshTokens.findByTokenHash(Sha256.hex(rawToken)).orElseThrow(SessionService::sessionEnded);
        User user = token.getUser();
        if (token.isRevoked()) {
            if (!token.wasRevokedWithin(RETRY_GRACE, now)) {
                user.endAllSessions();
                log.warn("Retired refresh token used again for user {}: all sessions ended", user.getId());
            }
            throw sessionEnded();
        }
        // needsTwoFactorSetup: an admin session from before two-factor login was required.
        if (!token.isUsableAt(now) || !user.isVerified() || !user.isActive() || user.needsTwoFactorSetup()) {
            throw sessionEnded();
        }
        token.revoke(now);
        return open(user);
    }

    /** Logs out one device. Unknown or already retired tokens are ignored. */
    @Transactional
    public void close(String rawToken) {
        refreshTokens.findByTokenHash(Sha256.hex(rawToken)).ifPresent(token -> token.revoke(LocalDateTime.now(clock)));
    }

    /** Once a day: expired tokens can never be used again, so they are deleted. */
    @Scheduled(initialDelay = 1, fixedDelay = 24, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void deleteExpired() {
        int deleted = refreshTokens.deleteExpiredBefore(LocalDateTime.now(clock));
        if (deleted > 0) {
            log.info("Deleted {} expired refresh token(s)", deleted);
        }
    }

    private static UnauthorizedException sessionEnded() {
        return new UnauthorizedException("SESSION_ENDED", "Your session has ended. Please log in again.");
    }
}
