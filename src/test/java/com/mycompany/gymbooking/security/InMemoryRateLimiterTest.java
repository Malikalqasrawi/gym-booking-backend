package com.mycompany.gymbooking.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.gymbooking.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The token bucket: 10 requests per minute per visitor, refilling a little every 6 seconds. */
class InMemoryRateLimiterTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T09:00:00Z"), ZoneOffset.UTC);
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

    @Test
    @DisplayName("10 requests pass, the 11th is blocked with a Retry-After")
    void blocksAfterLimit() {
        for (int i = 1; i <= 10; i++) {
            assertTrue(limiter.tryConsume("auth:1.2.3.4", 10, MINUTE).allowed(), "request " + i);
        }
        RateLimiter.Decision eleventh = limiter.tryConsume("auth:1.2.3.4", 10, MINUTE);
        assertFalse(eleventh.allowed());
        assertEquals(6L, eleventh.retryAfterSeconds(), "one token comes back every 6 s");
    }

    @Test
    @DisplayName("visitors don't share buckets")
    void separateVisitors() {
        for (int i = 0; i < 10; i++) {
            limiter.tryConsume("auth:1.1.1.1", 10, MINUTE);
        }
        assertFalse(limiter.tryConsume("auth:1.1.1.1", 10, MINUTE).allowed());
        assertTrue(limiter.tryConsume("auth:2.2.2.2", 10, MINUTE).allowed());
    }

    @Test
    @DisplayName("after waiting, requests are allowed again")
    void refills() {
        for (int i = 0; i < 10; i++) {
            limiter.tryConsume("k", 10, MINUTE);
        }
        assertFalse(limiter.tryConsume("k", 10, MINUTE).allowed());
        clock.advance(Duration.ofSeconds(6));
        assertTrue(limiter.tryConsume("k", 10, MINUTE).allowed());
        assertFalse(limiter.tryConsume("k", 10, MINUTE).allowed(), "only one token came back");
        clock.advance(MINUTE);
        for (int i = 1; i <= 10; i++) {
            assertTrue(limiter.tryConsume("k", 10, MINUTE).allowed(), "full again: request " + i);
        }
    }
}
