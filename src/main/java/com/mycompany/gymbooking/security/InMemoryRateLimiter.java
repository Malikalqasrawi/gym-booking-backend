package com.mycompany.gymbooking.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * The TOKEN BUCKET algorithm, with the counters kept in this server's memory.
 *
 * Picture one bucket per visitor that holds at most `limit` tokens (e.g. 10):
 *   - every request takes 1 token out
 *   - tokens drip back in at a steady speed: `limit` per window (10 per minute = 1 every 6 seconds)
 *   - bucket empty → request refused, and we know exactly when the next token arrives
 *
 * So a normal user tapping quickly is fine (the bucket starts full),
 * but a script sending non-stop gets at most 1 request every 6 seconds.
 *
 * "In memory" means the counters reset when the backend restarts, and aren't shared between
 * servers. That's fine for one server; see the RateLimiter interface for the multi-server plan.
 */
@Component
public class InMemoryRateLimiter implements RateLimiter {

    /** Every N requests we forget full buckets, so memory doesn't grow forever. */
    private static final int CLEANUP_EVERY_N_CALLS = 1_000;

    private final Clock clock;
    // ConcurrentHashMap: many requests arrive at the same time (one thread each), and this map is
    // safe to use from many threads. compute(...) below updates one key at a time, atomically.
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Decision tryConsume(String key, int limit, Duration window) {
        long now = clock.millis();
        double tokensPerMilli = (double) limit / window.toMillis();   // 10 per minute = 0.000166… per ms
        Decision[] answer = new Decision[1];

        buckets.compute(key, (k, bucket) -> {
            if (bucket == null) {
                bucket = new Bucket(limit, now);       // first visit: a full bucket
            }
            bucket.refill(now, limit, tokensPerMilli);

            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                answer[0] = Decision.allow();
            } else {
                // How long until the bucket reaches 1 token again?
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

    /** A full bucket behaves exactly like "never seen before", so we can delete it. */
    private void forgetFullBuckets(long now) {
        for (String key : buckets.keySet()) {
            // Returning null from computeIfPresent removes the entry (safely, one key at a time)
            buckets.computeIfPresent(key, (k, bucket) -> bucket.fullAgainAt <= now ? null : bucket);
        }
    }

    /** How many visitors we're tracking right now (used by tests). */
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

        /** Adds the tokens that dripped in since the last visit (never more than the limit). */
        void refill(long now, int limit, double tokensPerMilli) {
            long elapsed = Math.max(0, now - lastRefillMillis);
            tokens = Math.min(limit, tokens + elapsed * tokensPerMilli);
            lastRefillMillis = now;
        }
    }
}
