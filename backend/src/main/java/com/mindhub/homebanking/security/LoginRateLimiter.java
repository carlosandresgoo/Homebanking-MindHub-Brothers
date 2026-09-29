package com.mindhub.homebanking.security;

import com.mindhub.homebanking.config.SecurityProperties;
import com.mindhub.homebanking.exception.TooManyRequestsException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * In-memory, per-client-IP token bucket for the login endpoint (brute-force protection).
 * The map is LRU-bounded so a flood of distinct IPs cannot exhaust memory.
 * With several backend instances this should move to a shared store (e.g. bucket4j-redis).
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final SecurityProperties.LoginRateLimit config;
    private final Map<String, Bucket> buckets = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                    return size() > MAX_TRACKED_KEYS;
                }
            });

    public LoginRateLimiter(SecurityProperties properties) {
        this.config = properties.loginRateLimit();
    }

    /** @throws TooManyRequestsException when {@code key} has exhausted its attempts for the current period. */
    public void consume(String key) {
        ConsumptionProbe probe = buckets.computeIfAbsent(key, k -> newBucket()).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
            throw new TooManyRequestsException(retryAfter);
        }
    }

    /** Test support: forgets all tracked keys. */
    public void reset() {
        buckets.clear();
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(config.capacity())
                        .refillIntervally(config.capacity(), config.period())
                        .build())
                .build();
    }
}
