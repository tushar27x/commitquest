package com.tushar27x.insightshub.ratelimit;

import com.tushar27x.insightshub.ratelimit.RateLimitProperties.Policy;

import java.time.Duration;

/** Counts hits per key and decides whether one more is allowed. */
public interface RateLimiter {

    /** Records one hit for {@code key} and says whether it fits in {@code policy}. */
    Decision tryAcquire(String key, Policy policy);

    /**
     * @param remaining  hits left in the current window (0 when denied)
     * @param retryAfter time until the window resets
     */
    record Decision(boolean allowed, int limit, int remaining, Duration retryAfter) {

        /** Used when the limiter can't answer (Redis down): let the request through. */
        static Decision unlimited(Policy policy) {
            return new Decision(true, policy.requests(), policy.requests(), Duration.ZERO);
        }
    }
}
