package com.tushar27x.insightshub.ratelimit;

import com.tushar27x.insightshub.ratelimit.RateLimitProperties.Policy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Fixed-window counter in Redis: the first hit creates {@code key} with the window as its expiry,
 * each hit increments it, and the window resets when the key expires.
 * <p>
 * INCR and PEXPIRE run in one Lua script, so they're atomic: with two separate commands, a crash
 * in between would leave a counter that never expires and lock that client out for good.
 * <p>
 * Fails open: if Redis can't answer, the request is allowed and a warning is logged. A cache outage
 * shouldn't take logins down with it.
 */
@Slf4j
@Component
public class RedisRateLimiter implements RateLimiter {

    /** v1 shares this Redis; v2's keys get their own prefix. */
    static final String KEY_PREFIX = "v2:rl:";

    /**
     * KEYS[1] = counter, ARGV[1] = window in ms. Returns {count, ms until the window resets}.
     * A key without an expiry (PTTL = -1) gets one again, so it can't stick forever.
     */
    static final RedisScript<List> SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Decision tryAcquire(String key, Policy policy) {
        List<?> result;
        try {
            result = redis.execute(SCRIPT, List.of(KEY_PREFIX + key), Long.toString(policy.per().toMillis()));
        } catch (DataAccessException e) {
            log.warn("Rate limiter unavailable, allowing '{}': {}", key, e.getMessage());
            return Decision.unlimited(policy);
        }
        if (result == null || result.size() != 2) {
            log.warn("Rate limiter returned {} for '{}', allowing", result, key);
            return Decision.unlimited(policy);
        }
        long count = ((Number) result.get(0)).longValue();
        Duration retryAfter = Duration.ofMillis(((Number) result.get(1)).longValue());
        int remaining = (int) Math.max(0, policy.requests() - count);
        return new Decision(count <= policy.requests(), policy.requests(), remaining, retryAfter);
    }
}
