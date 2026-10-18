package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiInsightsService;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.github.GitHubAuthException;
import com.tushar27x.insightshub.github.GitHubGraphQlClient;
import com.tushar27x.insightshub.github.model.GitHubUser;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import com.tushar27x.insightshub.github.model.UserInsightsData;
import com.tushar27x.insightshub.hero.HeroService;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * One user's sync: GitHub → daily activity (calendar + branch scan) → stats and archetype → AI texts →
 * database (v1: the body of {@code callback()}).
 * Needs only a user id, so the login callback, refresh and the daily {@link ScheduledSync} can all call it.
 * <p>
 * The slow steps (GitHub, LLMs: seconds each) run outside any transaction; only the final write in
 * {@link ActivityWriter} and {@link InsightsWriter} hold a database connection.
 */
@Slf4j
@Service
public class SyncService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    /** GitHub allows at most a year between {@code from} and {@code to}. */
    static final Duration MAX_REFRESH = Duration.ofDays(365);

    private final UsersRepository usersRepository;
    private final TokenCipher tokenCipher;
    private final GitHubGraphQlClient gitHub;
    private final InsightsEngine engine;
    private final AiInsightsService ai;
    private final InsightsWriter writer;
    private final ActivityWriter activityWriter;
    private final BranchScanner branchScanner;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final SyncProperties properties;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimits;
    private final HeroService heroService;

    public SyncService(UsersRepository usersRepository, TokenCipher tokenCipher, GitHubGraphQlClient gitHub,
                       InsightsEngine engine, AiInsightsService ai, InsightsWriter writer,
                       ActivityWriter activityWriter, BranchScanner branchScanner, JsonMapper jsonMapper,
                       Clock clock, SyncProperties properties, RateLimiter rateLimiter,
                       RateLimitProperties rateLimits, HeroService heroService) {
        this.usersRepository = usersRepository;
        this.tokenCipher = tokenCipher;
        this.gitHub = gitHub;
        this.engine = engine;
        this.ai = ai;
        this.writer = writer;
        this.activityWriter = activityWriter;
        this.branchScanner = branchScanner;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.rateLimits = rateLimits;
        this.heroService = heroService;
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
     * The daily sync's entry point. Claims the cooldown like a user's sync does, so a refresh can't
     * run alongside it, and skips users who synced in the last {@code min-interval}. It doesn't take
     * the global cap: {@link ScheduledSync} bounds its own concurrency, and user syncs keep the whole cap.
     */
    public SyncOutcome scheduledSync(long userId) {
        Users user = load(userId);
        Instant now = clock.instant();
        if (usersRepository.claimSync(userId, now, now.minus(properties.minInterval())) == 0) {
            log.info("Scheduled sync for '{}' skipped: synced less than {} ago", user.getLogin(), properties.minInterval());
            return SyncOutcome.COOLDOWN;
        }
        return sync(user);
    }

    /**
     * Always syncs, without the cooldown. Returns
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

        // First, so the hero's activity is saved even if a later step fails
        Activity activity = writeActivity(user, token, data);

        InsightsResult result = engine.calculate(data.user());
        AiInsights texts = ai.generate(user.getLogin(), result);
        Map<String, Object> stats = jsonMapper.convertValue(result.stats(), MAP); // snake_case, v1's shape

        writer.save(userId, stats, result, texts, clock.instant());
        seedHeroQuietly(user);
        log.info("Sync for '{}' done in {} ms: {}, {} activity days written, branch scan {}", user.getLogin(),
                (System.nanoTime() - start) / 1_000_000, result.archetype(), activity.days(), activity.branchCounts());
        return SyncOutcome.SYNCED;
    }

    /**
     * The hero job's refresh before it settles days: the contribution calendar from {@code from} and the branch
     * scan, written to {@code daily_contributions}. No insights, no AI and no cooldown: it's one or two cheap
     * GitHub calls on the user's own token.
     *
     * @param from the oldest local day the job will settle; a day of slack covers every zone
     * @return {@code SYNCED}, or {@code NEEDS_REAUTH} (and the user is flagged) when GitHub rejects the token.
     * Any other GitHub failure is thrown.
     */
    public SyncOutcome refreshActivity(long userId, LocalDate from) {
        Users user = load(userId);
        if (user.isNeedsReauth() || user.getEncryptedGithubToken() == null) {
            return SyncOutcome.NEEDS_REAUTH;
        }
        String token = tokenCipher.decrypt(user.getEncryptedGithubToken(), userId);
        Instant now = clock.instant();
        Instant since = from.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        if (since.isBefore(now.minus(MAX_REFRESH))) {
            since = now.minus(MAX_REFRESH);
        }
        if (!since.isBefore(now)) {
            since = now.minus(Duration.ofDays(1));
        }

        UserInsightsData data;
        try {
            data = gitHub.fetchUserInsights(token, user.getLogin(), since, now);
        } catch (GitHubAuthException e) {
            log.warn("Activity refresh for '{}': GitHub rejected the stored token; marked needs_reauth", user.getLogin());
            writer.markNeedsReauth(userId);
            return SyncOutcome.NEEDS_REAUTH;
        }
        Activity activity = writeActivity(user, token, data);
        log.info("Activity refresh for '{}': {} days written, branch scan {}", user.getLogin(), activity.days(),
                activity.branchCounts());
        return SyncOutcome.SYNCED;
    }

    /** The calendar, then the branch scan for the recent days it shows as empty, in the user's zone. */
    private Activity writeActivity(Users user, String token, UserInsightsData data) {
        long userId = user.getGithubId();
        List<ContributionDay> calendar = InsightsEngine.sortedDays(contributions(data.user()));
        int days = activityWriter.writeCalendar(userId, calendar);
        ZoneId zone = HeroService.zoneOf(user.getTimezone());
        Map<LocalDate, Integer> branchCounts = branchScanner.scan(token, nodeId(data.user()), calendar, zone);
        if (!branchCounts.isEmpty()) {
            activityWriter.writeBranchCounts(userId, branchCounts);
        }
        return new Activity(days, branchCounts);
    }

    /** A new user's hero is created from the activity just written. Its failure mustn't fail the sync: the next one retries. */
    private void seedHeroQuietly(Users user) {
        try {
            heroService.seedIfMissing(user.getGithubId());
        } catch (RuntimeException e) {
            log.error("Creating the hero for '{}' failed; the next sync retries", user.getLogin(), e);
        }
    }

    private record Activity(int days, Map<LocalDate, Integer> branchCounts) {
    }

    private static GitHubUser.ContributionsCollection contributions(GitHubUser gitHubUser) {
        return gitHubUser == null ? null : gitHubUser.contributionsCollection();
    }

    private static String nodeId(GitHubUser gitHubUser) {
        return gitHubUser == null ? null : gitHubUser.id();
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
