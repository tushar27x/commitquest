package com.tushar27x.insightshub.ratelimit;

import com.tushar27x.insightshub.ratelimit.RateLimitProperties.Policy;
import com.tushar27x.insightshub.ratelimit.RateLimiter.Decision;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Java side of the limiter, with Redis mocked. The Lua script itself needs a real Redis
 * (Phase 14, Testcontainers); it was checked live.
 */
class RedisRateLimiterTest {

    private static final Policy FIVE_PER_MINUTE = new Policy(5, Duration.ofMinutes(1));

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedisRateLimiter limiter = new RedisRateLimiter(redis);

    @SuppressWarnings("unchecked")
    private void redisAnswers(long count, long ttlMillis) {
        when(redis.execute(eq(RedisRateLimiter.SCRIPT), anyList(), any(Object[].class)))
                .thenReturn(List.of(count, ttlMillis));
    }

    @Test
    void underTheLimitIsAllowed() {
        redisAnswers(3, 42_000);

        Decision decision = limiter.tryAcquire("login:ip:203.0.113.7", FIVE_PER_MINUTE);

        assertThat(decision).isEqualTo(new Decision(true, 5, 2, Duration.ofSeconds(42)));
    }

    @Test
    void theLastAllowedHitLeavesZero() {
        redisAnswers(5, 10_000);

        assertThat(limiter.tryAcquire("k", FIVE_PER_MINUTE)).isEqualTo(new Decision(true, 5, 0, Duration.ofSeconds(10)));
    }

    @Test
    void overTheLimitIsDenied() {
        redisAnswers(6, 10_000);

        Decision decision = limiter.tryAcquire("k", FIVE_PER_MINUTE);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.remaining()).isZero();
        assertThat(decision.retryAfter()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesThePrefixedKeyAndTheWindowInMillis() {
        redisAnswers(1, 60_000);

        limiter.tryAcquire("login:ip:203.0.113.7", FIVE_PER_MINUTE);

        verify(redis).execute(RedisRateLimiter.SCRIPT, List.of("v2:rl:login:ip:203.0.113.7"), "60000");
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisDownFailsOpen() {
        when(redis.execute(eq(RedisRateLimiter.SCRIPT), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("Unable to connect"));

        Decision decision = limiter.tryAcquire("k", FIVE_PER_MINUTE);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(5);
    }

    @Test
    void unexpectedAnswerFailsOpen() {
        when(redis.execute(eq(RedisRateLimiter.SCRIPT), anyList(), any(Object[].class))).thenReturn(null);

        assertThat(limiter.tryAcquire("k", FIVE_PER_MINUTE).allowed()).isTrue();
    }
}
