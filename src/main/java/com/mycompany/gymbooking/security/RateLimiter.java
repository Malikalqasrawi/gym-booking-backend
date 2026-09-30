package com.mycompany.gymbooking.security;

import java.time.Duration;

/**
 * INTERFACE: "can this visitor make another request right now?"
 *
 * RateLimitFilter only knows this interface. Today the answer comes from InMemoryRateLimiter
 * (counters in this server's memory). If we ever run 2+ servers, we'd write a RedisRateLimiter
 * (counters shared by all servers) and nothing else would change.
 */
public interface RateLimiter {

    /**
     * Counts one request for this key (e.g. "auth:192.168.1.20").
     *
     * @param key    who is asking (rule name + IP address)
     * @param limit  how many requests are allowed per window, e.g. 10
     * @param window the time window, e.g. 1 minute
     */
    Decision tryConsume(String key, int limit, Duration window);

    /** The answer: allowed, or blocked plus how many seconds to wait. */
    record Decision(boolean allowed, long retryAfterSeconds) {

        public static Decision allow() {
            return new Decision(true, 0);
        }

        public static Decision block(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}
