package com.mycompany.gymbooking.security;

import com.mycompany.gymbooking.model.User;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Locks out password and two-factor code guessing without letting an attacker lock out the owner.
 * Wrong attempts lock the account for the network address (IP) they came from, after
 * app.login.max-attempts within app.login.lock-minutes. The owner can still log in from anywhere
 * else. As a backstop against guesses spread over many addresses, app.login.account-max-attempts
 * wrong attempts in a row, from any addresses, lock the account everywhere.
 *
 * The per-address counts are kept in memory, like the rate limits, so they reset on restart; the
 * account-wide count is stored with the user.
 */
@Component
public class LoginGuard {

    private static final int CLEANUP_EVERY_N_CALLS = 1_000;

    private final int maxAttemptsPerAddress;
    private final int maxAttemptsPerAccount;
    private final long lockMinutes;
    private final ConcurrentHashMap<Key, Attempts> attempts = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    private record Key(Long userId, String address) {
    }

    private static final class Attempts {
        int wrong;
        LocalDateTime firstWrongAt;
        LocalDateTime lockedUntil;
    }

    public LoginGuard(@Value("${app.login.max-attempts}") int maxAttemptsPerAddress,
                      @Value("${app.login.account-max-attempts}") int maxAttemptsPerAccount,
                      @Value("${app.login.lock-minutes}") long lockMinutes) {
        this.maxAttemptsPerAddress = maxAttemptsPerAddress;
        this.maxAttemptsPerAccount = maxAttemptsPerAccount;
        this.lockMinutes = lockMinutes;
    }

    /** When the lock for this user and address ends, if there is one now. */
    public Optional<LocalDateTime> lockedUntil(User user, String address, LocalDateTime now) {
        LocalDateTime until = null;
        Attempts here = attempts.get(new Key(user.getId(), address));
        if (here != null) {
            synchronized (here) {
                if (here.lockedUntil != null && now.isBefore(here.lockedUntil)) {
                    until = here.lockedUntil;
                }
            }
        }
        if (user.isLoginLocked(now) && (until == null || user.getLoginLockedUntil().isAfter(until))) {
            until = user.getLoginLockedUntil();
        }
        return Optional.ofNullable(until);
    }

    /**
     * Counts a wrong password or code. The caller saves the user.
     * @return true if this attempt started a lock
     */
    public boolean recordWrong(User user, String address, LocalDateTime now) {
        LocalDateTime lockUntil = now.plusMinutes(lockMinutes);
        boolean locked;
        Attempts here = attempts.computeIfAbsent(new Key(user.getId(), address), key -> new Attempts());
        synchronized (here) {
            if (here.firstWrongAt == null || !now.isBefore(here.firstWrongAt.plusMinutes(lockMinutes))) {
                here.wrong = 0;   // older wrong attempts no longer count
                here.firstWrongAt = now;
            }
            here.wrong++;
            locked = here.wrong >= maxAttemptsPerAddress;
            if (locked) {
                here.lockedUntil = lockUntil;
                here.wrong = 0;
                here.firstWrongAt = null;
            }
        }
        boolean lockedEverywhere = user.recordFailedLogin(maxAttemptsPerAccount, lockUntil);
        cleanUpNow(now);
        return locked || lockedEverywhere;
    }

    /** A correct password (without a code step) or code: clears the counts for this address and the account. */
    public void recordSuccess(User user, String address) {
        attempts.remove(new Key(user.getId(), address));
        user.recordSuccessfulLogin();
    }

    /** Proving the email (password reset) lifts every lock on the account. */
    public void clearAll(User user) {
        attempts.keySet().removeIf(key -> Objects.equals(key.userId(), user.getId()));
        user.recordSuccessfulLogin();
    }

    private void cleanUpNow(LocalDateTime now) {
        if (calls.incrementAndGet() % CLEANUP_EVERY_N_CALLS != 0) {
            return;
        }
        attempts.values().removeIf(entry -> {
            synchronized (entry) {
                boolean lockOver = entry.lockedUntil == null || !now.isBefore(entry.lockedUntil);
                boolean countOver = entry.firstWrongAt == null || !now.isBefore(entry.firstWrongAt.plusMinutes(lockMinutes));
                return lockOver && countOver;
            }
        });
    }
}
