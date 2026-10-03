package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.model.User;
import com.mycompany.gymbooking.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes member sign-ups that were never verified, so nobody can sign up with someone else's email
 * and keep it. Unverified accounts can't log in, so they have no bookings or other data.
 */
@Service
public class UnverifiedAccountCleanup {

    private static final Logger log = LoggerFactory.getLogger(UnverifiedAccountCleanup.class);

    private final UserRepository users;
    private final Clock clock;
    private final long keepHours;

    public UnverifiedAccountCleanup(UserRepository users,
                                    Clock clock,
                                    @Value("${app.verification.unverified-account-hours}") long keepHours) {
        this.users = users;
        this.clock = clock;
        this.keepHours = keepHours;
    }

    @Scheduled(initialDelay = 10, fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public int deleteOld() {
        List<User> old = users.findUnverifiedMembersBefore(LocalDateTime.now(clock).minusHours(keepHours));
        users.deleteAll(old);
        if (!old.isEmpty()) {
            log.info("Deleted {} sign-up(s) that were never verified", old.size());
        }
        return old.size();
    }
}
