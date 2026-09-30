package com.mycompany.gymbooking.security;

import java.time.Duration;

public interface RateLimiter {

    /**
     * Records one request for the key and decides whether it is allowed.
     *
     * @param key   rule name plus client IP
     * @param limit requests allowed per window
     */
    Decision tryConsume(String key, int limit, Duration window);

    record Decision(boolean allowed, long retryAfterSeconds) {

        public static Decision allow() {
            return new Decision(true, 0);
        }

        public static Decision block(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}
