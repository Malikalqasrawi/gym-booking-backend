package com.mycompany.gymbooking.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Token-bucket rate limiter held in process memory. Buckets start full, so short bursts are allowed,
 * and refill at `limit` tokens per window. State is per instance and lost on restart; running several
 * instances would need a shared implementation (such as Redis).
 */
@Component
public class InMemoryRateLimiter implements RateLimiter {

    /** Full buckets are purged every N calls to keep memory bounded. */
    private static final int CLEANUP_EVERY_N_CALLS = 1_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Decision tryConsume(String key, int limit, Duration window) {
        long now = clock.millis();
        double tokensPerMilli = (double) limit / window.toMillis();
        Decision[] answer = new Decision[1];

        buckets.compute(key, (k, bucket) -> {
            if (bucket == null) {
                bucket = new Bucket(limit, now);
            }
            bucket.refill(now, limit, tokensPerMilli);

            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                answer[0] = Decision.allow();
            } else {
                long waitMillis = (long) Math.ceil((1 - bucket.tokens) / tokensPerMilli);
                answer[0] = Decision.block(Math.max(1, (waitMillis + 999) / 1000));   // round up to seconds
            }
            bucket.fullAgainAt = now + (long) Math.ceil((limit - bucket.tokens) / tokensPerMilli);
            return bucket;
        });

        if (calls.incrementAndGet() % CLEANUP_EVERY_N_CALLS == 0) {
            forgetFullBuckets(now);
        }
        return answer[0];
    }

    /** A full bucket is equivalent to a missing one, so it can be dropped. */
    private void forgetFullBuckets(long now) {
        for (String key : buckets.keySet()) {
            buckets.computeIfPresent(key, (k, bucket) -> bucket.fullAgainAt <= now ? null : bucket);
        }
    }

    /** Visible for tests. */
    int trackedKeys() {
        return buckets.size();
    }

    private static final class Bucket {
        double tokens;
        long lastRefillMillis;
        long fullAgainAt;

        Bucket(int limit, long now) {
            this.tokens = limit;
            this.lastRefillMillis = now;
        }

        void refill(long now, int limit, double tokensPerMilli) {
            long elapsed = Math.max(0, now - lastRefillMillis);
            tokens = Math.min(limit, tokens + elapsed * tokensPerMilli);
            lastRefillMillis = now;
        }
    }
}
