package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInputs;
import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiInsightsService;
import com.tushar27x.insightshub.ai.AiProperties;
import com.tushar27x.insightshub.ai.Regeneration;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.entity.GitHubScopes;
import com.tushar27x.insightshub.github.GitHubAuthException;
import com.tushar27x.insightshub.github.GitHubScopeException;
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
import java.util.Optional;
import java.util.concurrent.Semaphore;

/**
 * One user's sync: GitHub → daily activity (calendar + branch scan) → stats and archetype → AI texts →
 * database (v1: the body of {@code callback()}).
 * Needs only a user id, so the login callback and refresh can call it. The hourly hero job calls
 * {@link #refreshActivity} instead: activity only, no AI.
 * <p>
 * When the LLM runs (B11, one combined call per user):
 * <ul>
 *   <li>{@link #sync}: only for a user with no texts yet (a new user's first login), inline, so the first Journal
 *       has real texts. Later logins and SAVE keep the stored texts and call nothing.</li>
 *   <li>{@link #refreshInsights}, the daily {@link ScheduledSync}: a full insights fetch, then the texts are
 *       rewritten only when {@link Regeneration} says the data moved since they were written, at most once per
 *       user per UTC day, and at most {@code insightshub.ai.regenerate.concurrency} calls at once.</li>
 * </ul>
 * Nothing calls the LLM at startup.
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
    private final AiProperties.Regenerate regenerate;
    private final Semaphore llmPermits;

    public SyncService(UsersRepository usersRepository, TokenCipher tokenCipher, GitHubGraphQlClient gitHub,
                       InsightsEngine engine, AiInsightsService ai, InsightsWriter writer,
                       ActivityWriter activityWriter, BranchScanner branchScanner, JsonMapper jsonMapper,
                       Clock clock, SyncProperties properties, RateLimiter rateLimiter,
                       RateLimitProperties rateLimits, HeroService heroService, AiProperties aiProperties) {
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
        this.regenerate = aiProperties.regenerate();
        this.llmPermits = new Semaphore(regenerate.concurrency());
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

        Fetched fetched;
        try {
            Instant to = clock.instant();
            fetched = fetch(user, token, to.minus(MAX_REFRESH), to);
        } catch (GitHubAuthException e) {
            rejected("Sync", user, e);
            return SyncOutcome.NEEDS_REAUTH;
        }
        UserInsightsData data = fetched.data();

        // First, so the hero's activity is saved even if a later step fails
        boolean full = user.isPrivateResyncDue() || fetched.scopeLost(); // B11: a full resync owed, or private just lost
        Activity activity = writeActivity(user, token, data, fetched.includePrivate(), full, full);

        InsightsResult result = engine.calculate(data.user(), fetched.includePrivate())
                .playing(user.getChosenArchetype()); // the texts name the class played, not the scored one
        Map<String, Object> stats = jsonMapper.convertValue(result.stats(), MAP); // snake_case, v1's shape

        // B11: the LLM only for a user with no texts yet, or whose texts name another class (a class change);
        // everyone else keeps theirs until the daily job
        Regeneration.Stored storedTexts = writer.storedTexts(userId);
        boolean firstTexts = !storedTexts.hasTexts() || classChanged(storedTexts, result);
        if (firstTexts) {
            AiInsights texts = ai.generate(user.getLogin(), result);
            writer.save(userId, stats, result, texts, clock.instant());
        } else {
            writer.saveKeepingTexts(userId, stats, result, clock.instant());
        }
        resyncDone(user);
        seedHeroQuietly(user);
        log.info("Sync for '{}' done in {} ms: {}, {} activity days written, branch scan {}, AI texts {}",
                user.getLogin(), (System.nanoTime() - start) / 1_000_000, result.archetype(), activity.days(),
                activity.branchCounts(), firstTexts ? "written (first sync or class change)" : "kept");
        return SyncOutcome.SYNCED;
    }

    /** The stored texts were written for another class than the one played now (#74). */
    private static boolean classChanged(Regeneration.Stored stored, InsightsResult result) {
        return stored.inputs() != null && stored.inputs().archetype() != result.playedClass();
    }

    /**
     * The daily {@link ScheduledSync} for one user (B11): a full insights fetch with the user's own token (about 1
     * GitHub point), the activity, stats and scores written as in {@link #sync}, and the AI texts rewritten only if
     * {@link Regeneration} finds the data moved since they were written. No cooldown and no global cap: the user
     * didn't start it, and the LLM calls are bounded by {@code insightshub.ai.regenerate.concurrency}.
     *
     * @return what happened to the texts, or {@code NEEDS_REAUTH} (and the user is flagged) when GitHub rejects the
     * token. Any other GitHub failure is thrown, and nothing is written.
     */
    public DailyOutcome refreshInsights(long userId) {
        Users user = load(userId);
        if (user.isNeedsReauth() || user.getEncryptedGithubToken() == null) {
            return DailyOutcome.NEEDS_REAUTH;
        }
        long start = System.nanoTime();
        String token = tokenCipher.decrypt(user.getEncryptedGithubToken(), userId);

        Fetched fetched;
        try {
            Instant to = clock.instant();
            fetched = fetch(user, token, to.minus(MAX_REFRESH), to);
        } catch (GitHubAuthException e) {
            rejected("Daily insights refresh", user, e);
            return DailyOutcome.NEEDS_REAUTH;
        }
        UserInsightsData data = fetched.data();
        boolean full = user.isPrivateResyncDue() || fetched.scopeLost();
        Activity activity = writeActivity(user, token, data, fetched.includePrivate(), full, full);

        InsightsResult result = engine.calculate(data.user(), fetched.includePrivate())
                .playing(user.getChosenArchetype());
        Map<String, Object> stats = jsonMapper.convertValue(result.stats(), MAP);
        Optional<Regeneration.Reason> reason = Regeneration.decide(writer.storedTexts(userId), AiInputs.of(result),
                clock.instant(), regenerate);

        DailyOutcome outcome;
        if (reason.isPresent()) {
            AiInsights texts = generateWithPermit(user.getLogin(), result);
            writer.save(userId, stats, result, texts, clock.instant());
            outcome = texts.fallback() ? DailyOutcome.FALLBACK : DailyOutcome.REGENERATED;
        } else {
            writer.saveKeepingTexts(userId, stats, result, clock.instant());
            outcome = DailyOutcome.UNCHANGED;
        }
        resyncDone(user);
        seedHeroQuietly(user);
        log.info("Daily insights refresh for '{}' done in {} ms: {}, {} activity days written, branch scan {}, "
                        + "AI texts {}, token obtained {}", user.getLogin(), (System.nanoTime() - start) / 1_000_000,
                result.archetype(), activity.days(), activity.branchCounts(),
                reason.map(r -> "rewritten (" + r + ")" + (outcome == DailyOutcome.FALLBACK ? " with fallbacks" : ""))
                        .orElse("kept"), obtained(user)); // B43: which token worked
        return outcome;
    }

    private AiInsights generateWithPermit(String login, InsightsResult result) {
        llmPermits.acquireUninterruptibly();
        try {
            return ai.generate(login, result);
        } finally {
            llmPermits.release();
        }
    }

    /**
     * The hourly hero job, before it settles days: the contribution calendar from
     * {@code from} and the branch scan, written to {@code daily_contributions}. No insights, no AI and no cooldown:
     * it's one or two cheap GitHub calls on the user's own token.
     *
     * @param from the oldest local day to refresh; a day of slack covers every zone
     * @return {@code SYNCED}, or {@code NEEDS_REAUTH} (and the user is flagged) when GitHub rejects the token.
     * Any other GitHub failure is thrown.
     */
    public SyncOutcome refreshActivity(long userId, LocalDate from) {
        Users user = load(userId);
        if (user.isNeedsReauth() || user.getEncryptedGithubToken() == null) {
            return SyncOutcome.NEEDS_REAUTH;
        }
        if (user.isPrivateResyncDue()) {
            log.info("Activity refresh for '{}': a full resync is owed (private access changed), running it first",
                    user.getLogin());
            return sync(user); // B11: the full sync clears the flag
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

        Fetched fetched;
        try {
            fetched = fetch(user, token, since, now);
        } catch (GitHubAuthException e) {
            rejected("Activity refresh", user, e);
            return SyncOutcome.NEEDS_REAUTH;
        }
        if (fetched.scopeLost()) {
            // B11: private access was lost; this path is activity-only, so the insights still hold private work:
            // the next hero or daily run does a full sync first
            markResyncDue(user);
        }
        Activity activity = writeActivity(user, token, fetched.data(), fetched.includePrivate(), false,
                fetched.scopeLost());
        log.info("Activity refresh for '{}': {} days written, branch scan {}, token obtained {}", user.getLogin(),
                activity.days(), activity.branchCounts(), obtained(user)); // B43: which token worked
        return SyncOutcome.SYNCED;
    }

    /**
     * B10: the one GitHub fetch of every sync path. Private repos are requested only when the user opted in
     * ({@code include_private}) <b>and</b> the recorded scopes include {@code repo}; otherwise the query asks for PUBLIC
     * repositories only, so nothing private is fetched. If GitHub refuses the private part (FORBIDDEN) or the response
     * shows the token lost {@code repo}, the new scopes are recorded ({@code private_access} becomes {@code none}) and
     * the sync goes on with public data: no error, no {@code needs_reauth}. A 401 still propagates.
     */
    private Fetched fetch(Users user, String token, Instant from, Instant to) {
        return fetch(user, token, from, to, user.isIncludePrivate());
    }

    /** {@code includeChoice} is the switch's value to fetch for: the stored one, or the one a PUT is about to store. */
    private Fetched fetch(Users user, String token, Instant from, Instant to, boolean includeChoice) {
        boolean wantPrivate = includeChoice && user.hasRepoScope();
        UserInsightsData data;
        try {
            data = gitHub.fetchUserInsights(token, user.getLogin(), from, to, wantPrivate);
        } catch (GitHubScopeException e) {
            if (!wantPrivate) {
                throw e;
            }
            log.warn("GitHub refused private access for '{}'; going on with public repos only", user.getLogin());
            recordScopes(user, withoutRepo(user.getGithubScopes()));
            return new Fetched(gitHub.fetchUserInsights(token, user.getLogin(), from, to, false), false, true);
        }
        boolean includePrivate = wantPrivate;
        boolean scopeLost = false;
        String reported = data.oauthScopes();
        if (reported != null && !GitHubScopes.sameSet(reported, user.getGithubScopes())) {
            recordScopes(user, GitHubScopes.normalize(reported));
            if (wantPrivate && !GitHubScopes.hasRepo(reported)) {
                log.warn("The token of '{}' no longer has the repo scope; private repos are not read", user.getLogin());
                includePrivate = false; // GitHub returned public data only
                scopeLost = true;
            }
        }
        return new Fetched(data, includePrivate, scopeLost);
    }

    private void recordScopes(Users user, String scopes) {
        try {
            usersRepository.updateGithubScopes(user.getGithubId(), scopes);
        } catch (RuntimeException e) {
            log.error("Could not record the GitHub scopes of '{}'", user.getLogin(), e);
        }
    }

    private static String withoutRepo(String scopes) {
        return GitHubScopes.parse(scopes).stream().filter(s -> !s.equals(GitHubScopes.REPO))
                .collect(java.util.stream.Collectors.joining(","));
    }

    /** @param scopeLost B11: private access was wanted and effective on paper, but GitHub no longer grants it */
    private record Fetched(UserInsightsData data, boolean includePrivate, boolean scopeLost) {
    }

    /**
     * B43: GitHub answered 401 for the stored token. Logs what GitHub support can trace (its request id and
     * message) and which token it was (when it was obtained, and its age), never the token. Flags the user
     * {@code needs_reauth} only if that token is still the stored one (B45).
     */
    private void rejected(String what, Users user, GitHubAuthException e) {
        boolean flagged = writer.markNeedsReauth(user.getGithubId(), user.getEncryptedGithubToken());
        log.warn("{} for '{}': GitHub rejected the stored token (HTTP 401, request id {}, message \"{}\"); "
                        + "token obtained {}; {}", what, user.getLogin(), orUnknown(e.requestId()),
                orUnknown(e.gitHubMessage()), obtained(user),
                flagged ? "marked needs_reauth" : "not flagged: a login stored a new token meanwhile");
    }

    /** "2026-11-04T18:30:00Z (8h 30m ago)", or "unknown (before V8)" for a token stored before V8. */
    private String obtained(Users user) {
        Instant obtainedAt = user.getGithubTokenObtainedAt();
        if (obtainedAt == null) {
            return "unknown (before V8)";
        }
        return obtainedAt + " (" + age(Duration.between(obtainedAt, clock.instant())) + " ago)";
    }

    static String age(Duration age) {
        if (age.isNegative()) {
            return "0m";
        }
        long days = age.toDays();
        String hoursMinutes = age.toHoursPart() + "h " + age.toMinutesPart() + "m";
        return days > 0 ? days + "d " + hoursMinutes : hoursMinutes;
    }

    private static String orUnknown(String value) {
        return value == null ? "unknown" : value;
    }

    /** The calendar, then the branch scan for the recent days it shows as empty, in the user's zone. */
    private Activity writeActivity(Users user, String token, UserInsightsData data, boolean includePrivate,
                                   boolean fullCalendar, boolean resetBranches) {
        long userId = user.getGithubId();
        List<ContributionDay> calendar = InsightsEngine.sortedDays(contributions(data.user()));
        int days = fullCalendar ? activityWriter.writeCalendarInFull(userId, calendar)
                : activityWriter.writeCalendar(userId, calendar);
        ZoneId zone = HeroService.zoneOf(user.getTimezone());
        Map<LocalDate, Integer> branchCounts = branchScanner.scan(token, nodeId(data.user()), calendar, zone, includePrivate);
        if (resetBranches) {
            // B11: the scan replaces the stored counts; with private access off, nothing else may keep a private count
            activityWriter.replaceBranchCounts(userId, branchCounts, !includePrivate);
        } else if (!branchCounts.isEmpty()) {
            activityWriter.writeBranchCounts(userId, branchCounts);
        }
        return new Activity(days, branchCounts);
    }

    /** B11: a full sync is done, so an owed resync is paid. */
    private void resyncDone(Users user) {
        if (user.isPrivateResyncDue()) {
            usersRepository.setPrivateResyncDue(user.getGithubId(), false);
        }
    }

    private void markResyncDue(Users user) {
        try {
            usersRepository.setPrivateResyncDue(user.getGithubId(), true);
        } catch (RuntimeException e) {
            log.error("Could not flag the owed resync of '{}'", user.getLogin(), e);
        }
    }

    /**
     * B11: the global sync cap ({@code sync-global}) for a sync the user started outside {@link #syncIfDue} (the
     * private switch's resync inside the PUT).
     *
     * @return empty if a slot was taken, else when a new attempt can succeed
     */
    public Optional<Duration> acquireGlobalSlot() {
        Decision global = rateLimiter.tryAcquire("sync:global", rateLimits.policy(RateLimitProperties.SYNC_GLOBAL));
        return global.allowed() ? Optional.empty() : Optional.of(global.retryAfter());
    }

    /**
     * B11: everything a full resync computes, with the GitHub calls and the LLM already done, and nothing written.
     * The private switch commits it together with the new switch value in one transaction ({@link ResyncWriter}), so
     * a failure before that leaves the stored data and the switch as they were.
     *
     * @param includePrivate what was actually fetched: false if the token turned out not to have {@code repo}
     * @param texts          null = keep the stored AI texts
     */
    public record PreparedResync(long userId, boolean includePrivate, boolean scopeLost,
                                 List<ContributionDay> calendar, Map<LocalDate, Integer> branchCounts,
                                 Map<String, Object> stats, InsightsResult result, AiInsights texts, Instant at) {
    }

    /**
     * B11: the fetch, scan, engine and AI of a full resync for {@code includeChoice} as the private switch's new
     * value, without the cooldown (the caller took a global slot) and without writing anything.
     *
     * @return empty when GitHub rejects the token (the user is flagged {@code needs_reauth}) or there is none
     * @throws com.tushar27x.insightshub.github.GitHubApiException any other GitHub failure
     */
    public Optional<PreparedResync> prepareResync(long userId, boolean includeChoice) {
        Users user = load(userId);
        if (user.isNeedsReauth() || user.getEncryptedGithubToken() == null) {
            return Optional.empty();
        }
        String token = tokenCipher.decrypt(user.getEncryptedGithubToken(), userId);
        Fetched fetched;
        Instant to = clock.instant();
        try {
            fetched = fetch(user, token, to.minus(MAX_REFRESH), to, includeChoice);
        } catch (GitHubAuthException e) {
            rejected("Private switch resync", user, e);
            return Optional.empty();
        }
        UserInsightsData data = fetched.data();
        List<ContributionDay> calendar = InsightsEngine.sortedDays(contributions(data.user()));
        Map<LocalDate, Integer> branchCounts = branchScanner.scan(token, nodeId(data.user()), calendar,
                HeroService.zoneOf(user.getTimezone()), fetched.includePrivate());
        InsightsResult result = engine.calculate(data.user(), fetched.includePrivate())
                .playing(user.getChosenArchetype());
        Map<String, Object> stats = jsonMapper.convertValue(result.stats(), MAP);
        AiInsights texts = writer.storedTexts(userId).hasTexts() ? null : ai.generate(user.getLogin(), result);
        return Optional.of(new PreparedResync(userId, fetched.includePrivate(), fetched.scopeLost(), calendar,
                branchCounts, stats, result, texts, clock.instant()));
    }

    /** After a committed {@link PreparedResync}: a new user's hero is created from the activity just written. */
    public void afterResync(long userId) {
        seedHeroQuietly(load(userId));
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

    /** {@link #refreshInsights}: what happened to the user's AI texts. */
    public enum DailyOutcome {
        /** Rewritten, every field from the LLM. */
        REGENERATED,
        /** Rewritten, but at least one field is a static fallback (retried on the next run). */
        FALLBACK,
        /** Nothing moved enough, or they were already written today: kept. */
        UNCHANGED,
        /** No usable GitHub token: the user must log in again. */
        NEEDS_REAUTH
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
