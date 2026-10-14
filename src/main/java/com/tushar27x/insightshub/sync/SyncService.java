package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiInsightsService;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.github.GitHubAuthException;
import com.tushar27x.insightshub.github.GitHubGraphQlClient;
import com.tushar27x.insightshub.github.model.UserInsightsData;
import com.tushar27x.insightshub.insights.InsightsEngine;
import com.tushar27x.insightshub.insights.InsightsResult;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import com.tushar27x.insightshub.ratelimit.RateLimiter;
import com.tushar27x.insightshub.ratelimit.RateLimiter.Decision;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.TokenCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * One user's sync: GitHub → stats and archetype → AI texts → database (v1: the body of {@code callback()}).
 * Needs only a user id, so the login callback and the Phase 10 scheduler can both call it.
 * <p>
 * The slow steps (GitHub, LLMs: seconds each) run outside any transaction; only the final write in
 * {@link InsightsWriter} holds a database connection.
 */
@Slf4j
@Service
public class SyncService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final UsersRepository usersRepository;
    private final TokenCipher tokenCipher;
    private final GitHubGraphQlClient gitHub;
    private final InsightsEngine engine;
    private final AiInsightsService ai;
    private final InsightsWriter writer;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final SyncProperties properties;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimits;

    public SyncService(UsersRepository usersRepository, TokenCipher tokenCipher, GitHubGraphQlClient gitHub,
                       InsightsEngine engine, AiInsightsService ai, InsightsWriter writer, JsonMapper jsonMapper,
                       Clock clock, SyncProperties properties, RateLimiter rateLimiter,
                       RateLimitProperties rateLimits) {
        this.usersRepository = usersRepository;
        this.tokenCipher = tokenCipher;
        this.gitHub = gitHub;
        this.engine = engine;
        this.ai = ai;
        this.writer = writer;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.rateLimits = rateLimits;
    }

    /**
     * A sync the user started (login or refresh). Three gates, cheapest first:
     * <ol>
     *   <li>the user's own 10-minute cooldown, read without writing, so spamming during a cooldown
     *       can't use up the global cap;</li>
     *   <li>the global cap on syncs across all users ({@code sync-global}), which protects the shared LLM key;</li>
     *   <li>the atomic claim of the cooldown, so double clicks can't start two syncs. A sync that
     *       fails after this point still counts.</li>
     * </ol>
     */
    public SyncResult syncIfDue(long userId) {
        Users user = load(userId);
        Instant now = clock.instant();
        Duration cooldownLeft = cooldownLeft(user.getSyncRequestedAt(), now);
        if (!cooldownLeft.isZero()) {
            log.info("Sync for '{}' skipped: cooldown, {} left", user.getLogin(), cooldownLeft);
            return new SyncResult(SyncOutcome.COOLDOWN, cooldownLeft);
        }

        Decision global = rateLimiter.tryAcquire("sync:global", rateLimits.policy(RateLimitProperties.SYNC_GLOBAL));
        if (!global.allowed()) {
            log.warn("Sync for '{}' skipped: global sync cap reached, resets in {}", user.getLogin(), global.retryAfter());
            return new SyncResult(SyncOutcome.BUSY, global.retryAfter());
        }

        if (usersRepository.claimSync(userId, now, now.minus(properties.minInterval())) == 0) {
            log.info("Sync for '{}' skipped: another request started one first", user.getLogin());
            return new SyncResult(SyncOutcome.COOLDOWN, properties.minInterval());
        }
        return new SyncResult(sync(user), Duration.ZERO);
    }

    private Duration cooldownLeft(Instant lastRequested, Instant now) {
        if (lastRequested == null) {
            return Duration.ZERO;
        }
        Duration left = Duration.between(now, lastRequested.plus(properties.minInterval()));
        return left.isNegative() ? Duration.ZERO : left;
    }

    /**
     * Always syncs, without the cooldown (for the Phase 10 scheduler). Returns
     * {@link SyncOutcome#NEEDS_REAUTH} (and flags the user) when GitHub rejects the stored token;
     * any other failure is thrown, and nothing is written.
     */
    public SyncOutcome sync(long userId) {
        return sync(load(userId));
    }

    private SyncOutcome sync(Users user) {
        long userId = user.getGithubId();
        if (user.isNeedsReauth() || user.getEncryptedGithubToken() == null) {
            log.info("Sync for '{}' skipped: needs to log in again", user.getLogin());
            return SyncOutcome.NEEDS_REAUTH;
        }
        long start = System.nanoTime();
        String token = tokenCipher.decrypt(user.getEncryptedGithubToken(), userId);

        UserInsightsData data;
        try {
            data = gitHub.fetchUserInsights(token, user.getLogin());
        } catch (GitHubAuthException e) {
            log.warn("Sync for '{}': GitHub rejected the stored token; marked needs_reauth", user.getLogin());
            writer.markNeedsReauth(userId);
            return SyncOutcome.NEEDS_REAUTH;
        }

        InsightsResult result = engine.calculate(data.user());
        AiInsights texts = ai.generate(user.getLogin(), result);
        Map<String, Object> stats = jsonMapper.convertValue(result.stats(), MAP); // snake_case, v1's shape

        writer.save(userId, stats, result, texts, clock.instant());
        log.info("Sync for '{}' done in {} ms: {}", user.getLogin(), (System.nanoTime() - start) / 1_000_000,
                result.archetype());
        return SyncOutcome.SYNCED;
    }

    private Users load(long userId) {
        return usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user " + userId));
    }

    /** @param retryAfter for {@code COOLDOWN} and {@code BUSY}: when a new attempt can succeed */
    public record SyncResult(SyncOutcome outcome, Duration retryAfter) {
    }

    public enum SyncOutcome {
        SYNCED,
        /** A sync was started less than {@code min-interval} ago; nothing was fetched. */
        COOLDOWN,
        /** The global cap on syncs per minute is reached; nothing was fetched. */
        BUSY,
        /** No usable GitHub token: the user must log in again. */
        NEEDS_REAUTH
    }
}
