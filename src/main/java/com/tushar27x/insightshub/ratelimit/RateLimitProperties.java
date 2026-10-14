package com.tushar27x.insightshub.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

/**
 * {@code insightshub.rate-limit.*}.
 *
 * @param enabled  switch off to bypass every limit (tests, local debugging)
 * @param policies named limits, referenced by {@link RateLimit#policy()} and by the sync's global cap
 */
@Validated
@ConfigurationProperties(prefix = "insightshub.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue Map<String, @Valid Policy> policies) {

    /** Policy name for the cap on syncs across all users (protects the shared LLM key). */
    public static final String SYNC_GLOBAL = "sync-global";

    /** At most {@code requests} per {@code per} window. */
    public record Policy(@Positive int requests, @NotNull Duration per) {
    }

    public Policy policy(String name) {
        Policy policy = policies.get(name);
        if (policy == null) {
            throw new IllegalStateException("No rate limit policy '" + name + "' in insightshub.rate-limit.policies");
        }
        return policy;
    }
}
