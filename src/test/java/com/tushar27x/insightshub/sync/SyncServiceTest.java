package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiField;
import com.tushar27x.insightshub.ai.AiInputs;
import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiInsightsService;
import com.tushar27x.insightshub.ai.AiProperties;
import com.tushar27x.insightshub.ai.Regeneration;
import com.tushar27x.insightshub.ai.AiTestData;
import com.tushar27x.insightshub.ai.CharacterSheet;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.github.GitHubAuthException;
import com.tushar27x.insightshub.github.GitHubGraphQlClient;
import com.tushar27x.insightshub.github.model.GitHubUser;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionCalendar;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionsCollection;
import com.tushar27x.insightshub.github.model.GitHubUser.Week;
import com.tushar27x.insightshub.github.model.UserInsightsData;
import com.tushar27x.insightshub.hero.HeroService;
import com.tushar27x.insightshub.insights.InsightsEngine;
import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.insights.InsightsResult;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.security.TokenCipher;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties.Policy;
import com.tushar27x.insightshub.ratelimit.RateLimiter;
import com.tushar27x.insightshub.ratelimit.RateLimiter.Decision;
import com.tushar27x.insightshub.sync.SyncService.DailyOutcome;
import com.tushar27x.insightshub.sync.SyncService.SyncOutcome;
import com.tushar27x.insightshub.sync.SyncService.SyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.ArgumentCaptor;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SyncServiceTest {

    private static final long ID = 583231L;
    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");
    private static final Duration COOLDOWN = Duration.ofMinutes(10);

    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final TokenCipher cipher = new TokenCipher(SecurityTestData.appProperties());
    private final GitHubGraphQlClient gitHub = mock(GitHubGraphQlClient.class);
    private final InsightsEngine engine = mock(InsightsEngine.class);
    private final AiInsightsService ai = mock(AiInsightsService.class);
    private final InsightsWriter writer = mock(InsightsWriter.class);
    private final ActivityWriter activityWriter = mock(ActivityWriter.class);
    private final BranchScanner branchScanner = mock(BranchScanner.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final HeroService heroService = mock(HeroService.class);
    private static final Policy GLOBAL = new Policy(30, Duration.ofMinutes(1));
    private final InsightsResult result = AiTestData.insights();
    private final AiInsights texts = new AiInsights("year", "week", "craft", Map.of("app", "An app."),
            new CharacterSheet("octocat the Monk", "Calm."));

    private static final ContributionDay OCT_12 = new ContributionDay(LocalDate.parse("2026-10-12"), 0);
    private static final ContributionDay OCT_13 = new ContributionDay(LocalDate.parse("2026-10-13"), 3);
    private static final ContributionDay OCT_14 = new ContributionDay(LocalDate.parse("2026-10-14"), 1);

    private SyncService service;
    private Users user;

    @BeforeEach
    void setUp() {
        service = new SyncService(usersRepository, cipher, gitHub, engine, ai, writer, activityWriter, branchScanner,
                JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC), new SyncProperties(COOLDOWN, 4, 2), rateLimiter,
                new RateLimitProperties(true, Map.of(RateLimitProperties.SYNC_GLOBAL, GLOBAL)), heroService,
                new AiProperties(null, null, Duration.ofSeconds(90), null));
        when(rateLimiter.tryAcquire("sync:global", GLOBAL)).thenReturn(new Decision(true, 30, 29, Duration.ofSeconds(50)));
        user = new Users(ID, "octocat");
        user.setEncryptedGithubToken(cipher.encrypt("gho_stored", ID));
        when(usersRepository.findById(ID)).thenReturn(Optional.of(user));

        // Weeks out of order, to check the calendar is sorted before it's written
        ContributionCalendar calendar = new ContributionCalendar(4,
                List.of(new Week(List.of(OCT_14)), new Week(List.of(OCT_12, OCT_13))));
        GitHubUser gitHubUser = new GitHubUser("U_octocat", "octocat", null, null, null, null, null, null, null, null,
                new ContributionsCollection(List.of(), calendar, 4, 0, 0, 0));
        UserInsightsData data = new UserInsightsData(null, gitHubUser);
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), any(), eq(false))).thenReturn(data);
        when(engine.calculate(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(result);
        when(ai.generate("octocat", result)).thenReturn(texts);
        when(branchScanner.scan(any(), any(), any(), any(), eq(false))).thenReturn(Map.of());
        when(writer.storedTexts(ID)).thenReturn(Regeneration.Stored.NONE); // a new user: no texts yet
    }

    @Test
    void syncsWithTheDecryptedTokenAndWritesEverything() {
        assertThat(service.sync(ID)).isEqualTo(SyncOutcome.SYNCED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> stats = ArgumentCaptor.forClass(Map.class);
        verify(writer).save(eq(ID), stats.capture(), eq(result), eq(texts), eq(NOW));
        assertThat(stats.getValue()).containsEntry("total_commits", 42) // v1's snake_case keys
                .containsKeys("top_languages", "last_week_heatmap", "code_churn");
    }

    @Test
    void aChangedClassRewritesTheTextsForTheClassPlayed() { // #74
        when(writer.storedTexts(ID)).thenReturn(new Regeneration.Stored(true, NOW.minus(Duration.ofHours(1)),
                AiInputs.of(result), false)); // written for the scored class
        user.setChosenArchetype(Archetype.ROGUE);
        InsightsResult rogue = result.playing(Archetype.ROGUE);
        when(ai.generate("octocat", rogue)).thenReturn(texts);

        service.sync(ID);

        verify(ai).generate("octocat", rogue);
        verify(writer).save(eq(ID), any(), eq(rogue), eq(texts), eq(NOW));
    }

    @Test
    void textsWrittenForTheClassPlayedAreKept() {
        user.setChosenArchetype(Archetype.ROGUE);
        InsightsResult rogue = result.playing(Archetype.ROGUE);
        when(writer.storedTexts(ID)).thenReturn(new Regeneration.Stored(true, NOW.minus(Duration.ofHours(1)),
                AiInputs.of(rogue), false));

        service.sync(ID);

        verify(ai, never()).generate(any(), any());
    }

    @Test
    void writesTheSortedCalendarBeforeTheSlowAiStep() {
        service.sync(ID);

        var order = inOrder(activityWriter, ai, writer);
        order.verify(activityWriter).writeCalendar(ID, List.of(OCT_12, OCT_13, OCT_14));
        order.verify(ai).generate("octocat", result);
        order.verify(writer).save(eq(ID), any(), eq(result), eq(texts), eq(NOW));
    }

    @Test
    void branchScanGetsTheNodeIdAndCalendarAndItsCountsAreWrittenBeforeAi() {
        List<ContributionDay> calendar = List.of(OCT_12, OCT_13, OCT_14);
        when(branchScanner.scan("gho_stored", "U_octocat", calendar, ZoneOffset.UTC, false))
                .thenReturn(Map.of(OCT_12.date(), 2));

        service.sync(ID);

        var order = inOrder(activityWriter, ai);
        order.verify(activityWriter).writeCalendar(ID, calendar);
        order.verify(activityWriter).writeBranchCounts(ID, Map.of(OCT_12.date(), 2));
        order.verify(ai).generate("octocat", result);
    }

    @Test
    void nothingScannedMeansNoBranchWrite() {
        service.sync(ID);

        verify(activityWriter, never()).writeBranchCounts(anyLong(), any());
    }

    @Test
    void responseWithoutAUserWritesNoDays() {
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), any(), eq(false))).thenReturn(new UserInsightsData(null, null));

        assertThat(service.sync(ID)).isEqualTo(SyncOutcome.SYNCED);
        verify(activityWriter).writeCalendar(ID, List.of());
    }

    @Test
    void dueSyncPassesAllGatesAndRuns() {
        user.setSyncRequestedAt(NOW.minus(Duration.ofMinutes(20)));
        when(usersRepository.claimSync(ID, NOW, NOW.minus(COOLDOWN))).thenReturn(1);

        assertThat(service.syncIfDue(ID)).isEqualTo(new SyncResult(SyncOutcome.SYNCED, Duration.ZERO));
        verify(writer).save(eq(ID), any(), eq(result), eq(texts), eq(NOW));
    }

    @Test
    void cooldownIsCheckedFirstAndUsesNoGlobalCapacity() {
        user.setSyncRequestedAt(NOW.minus(Duration.ofMinutes(4)));

        assertThat(service.syncIfDue(ID)).isEqualTo(new SyncResult(SyncOutcome.COOLDOWN, Duration.ofMinutes(6)));
        verifyNoInteractions(rateLimiter, gitHub, ai, writer, activityWriter);
        verify(usersRepository, never()).claimSync(anyLong(), any(), any());
    }

    @Test
    void globalCapReachedIsBusyAndDoesNotStartTheCooldown() {
        when(rateLimiter.tryAcquire("sync:global", GLOBAL)).thenReturn(new Decision(false, 30, 0, Duration.ofSeconds(17)));

        assertThat(service.syncIfDue(ID)).isEqualTo(new SyncResult(SyncOutcome.BUSY, Duration.ofSeconds(17)));
        verify(usersRepository, never()).claimSync(anyLong(), any(), any()); // the user can retry once capacity frees up
        verifyNoInteractions(gitHub, ai, writer, activityWriter);
    }

    @Test
    void losingTheClaimRaceIsCooldown() {
        when(usersRepository.claimSync(ID, NOW, NOW.minus(COOLDOWN))).thenReturn(0);

        assertThat(service.syncIfDue(ID)).isEqualTo(new SyncResult(SyncOutcome.COOLDOWN, COOLDOWN));
        verifyNoInteractions(gitHub, ai, writer, activityWriter);
    }

    @Test
    void rejectedTokenFlagsTheUserInsteadOfFailing() {
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), any(), eq(false))).thenThrow(new GitHubAuthException("401"));

        assertThat(service.sync(ID)).isEqualTo(SyncOutcome.NEEDS_REAUTH);
        verify(writer).markNeedsReauth(ID, user.getEncryptedGithubToken());
        verify(writer, never()).save(anyLong(), any(), any(), any(), any());
        verifyNoInteractions(ai, activityWriter, branchScanner);
    }

    @Test
    void flaggedUserIsNotSynced() {
        user.setNeedsReauth(true);

        assertThat(service.sync(ID)).isEqualTo(SyncOutcome.NEEDS_REAUTH);
        verifyNoInteractions(gitHub);
    }

    @Test
    void otherFailuresPropagateAndWriteNothing() {
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), any(), eq(false))).thenThrow(new GitHubApiException("HTTP 502"));

        assertThatThrownBy(() -> service.sync(ID)).isInstanceOf(GitHubApiException.class);
        verifyNoInteractions(writer, activityWriter, branchScanner);
    }

    @Test
    void unknownUserIsAnError() {
        assertThatThrownBy(() -> service.sync(42L)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- B5: the hero ---

    @Test
    void aSyncCreatesTheHeroAfterTheInsightsAreSaved() {
        service.sync(ID);

        var order = inOrder(activityWriter, writer, heroService);
        order.verify(activityWriter).writeCalendar(eq(ID), any());
        order.verify(writer).save(eq(ID), any(), eq(result), eq(texts), eq(NOW));
        order.verify(heroService).seedIfMissing(ID);
    }

    @Test
    void aFailedHeroSeedDoesNotFailTheSync() {
        when(heroService.seedIfMissing(ID)).thenThrow(new IllegalStateException("db down"));

        assertThat(service.sync(ID)).isEqualTo(SyncOutcome.SYNCED);
    }

    @Test
    void theBranchScanRunsInTheUsersZone() {
        user.setTimezone("Asia/Kolkata");

        service.sync(ID);

        verify(branchScanner).scan("gho_stored", "U_octocat", List.of(OCT_12, OCT_13, OCT_14), ZoneId.of("Asia/Kolkata"), false);
    }

    @Test
    void refreshWritesTheCalendarFromADayBeforeTheFirstDueDayWithoutAi() {
        UserInsightsData data = new UserInsightsData(null, new GitHubUser("U_octocat", "octocat", null, null, null, null,
                null, null, null, null, new ContributionsCollection(List.of(),
                new ContributionCalendar(1, List.of(new Week(List.of(OCT_13)))), 1, 0, 0, 0)));
        when(gitHub.fetchUserInsights("gho_stored", "octocat", Instant.parse("2026-10-12T00:00:00Z"), NOW, false))
                .thenReturn(data);
        when(branchScanner.scan("gho_stored", "U_octocat", List.of(OCT_13), ZoneOffset.UTC, false))
                .thenReturn(Map.of(OCT_14.date(), 1));

        assertThat(service.refreshActivity(ID, OCT_13.date())).isEqualTo(SyncOutcome.SYNCED);

        verify(activityWriter).writeCalendar(ID, List.of(OCT_13));
        verify(activityWriter).writeBranchCounts(ID, Map.of(OCT_14.date(), 1));
        verifyNoInteractions(ai, engine, rateLimiter, heroService);
        verify(writer, never()).save(anyLong(), any(), any(), any(), any());
        verify(usersRepository, never()).claimSync(anyLong(), any(), any());
    }

    @Test
    void refreshNeverAsksForMoreThanAYear() {
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), eq(NOW), eq(false)))
                .thenReturn(new UserInsightsData(null, null));

        service.refreshActivity(ID, LocalDate.parse("2020-01-01"));

        verify(gitHub).fetchUserInsights("gho_stored", "octocat", NOW.minus(Duration.ofDays(365)), NOW, false);
    }

    @Test
    void refreshWithARejectedTokenFlagsTheUser() {
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), any(), eq(false)))
                .thenThrow(new GitHubAuthException("401"));

        assertThat(service.refreshActivity(ID, OCT_13.date())).isEqualTo(SyncOutcome.NEEDS_REAUTH);
        verify(writer).markNeedsReauth(ID, user.getEncryptedGithubToken());
        verifyNoInteractions(activityWriter);
    }

    @Test
    void refreshSkipsAFlaggedUser() {
        user.setNeedsReauth(true);

        assertThat(service.refreshActivity(ID, OCT_13.date())).isEqualTo(SyncOutcome.NEEDS_REAUTH);
        verifyNoInteractions(gitHub);
    }

    @Test
    void refreshLetsOtherGitHubFailuresThrough() {
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), any(), eq(false)))
                .thenThrow(new GitHubApiException("HTTP 502"));

        assertThatThrownBy(() -> service.refreshActivity(ID, OCT_13.date())).isInstanceOf(GitHubApiException.class);
        verifyNoInteractions(activityWriter);
    }

    // --- B11: when the LLM runs ---

    /** Texts written yesterday from exactly today's inputs: nothing moved. */
    private void textsWrittenYesterdayFromTheSameData() {
        when(writer.storedTexts(ID)).thenReturn(
                new Regeneration.Stored(true, NOW.minus(Duration.ofDays(1)), AiInputs.of(result), false));
    }

    @Test
    void aLaterSyncKeepsTheStoredTextsAndCallsNoLlm() {
        textsWrittenYesterdayFromTheSameData();

        assertThat(service.sync(ID)).isEqualTo(SyncOutcome.SYNCED);

        verify(writer).saveKeepingTexts(eq(ID), any(), eq(result), eq(NOW));
        verify(writer, never()).save(anyLong(), any(), any(), any(), any());
        verifyNoInteractions(ai);
        verify(heroService).seedIfMissing(ID);
    }

    @Test
    void aLaterSyncKeepsEvenOldOrFallbackTextsThatsTheDailyJobsWork() {
        when(writer.storedTexts(ID)).thenReturn(new Regeneration.Stored(true, null, null, true));

        service.sync(ID);

        verifyNoInteractions(ai);
    }

    @Test
    void theDailyRefreshFetchesTheFullInsightsAndKeepsTextsWhenNothingMoved() {
        textsWrittenYesterdayFromTheSameData();

        assertThat(service.refreshInsights(ID)).isEqualTo(DailyOutcome.UNCHANGED);

        verify(gitHub).fetchUserInsights("gho_stored", "octocat", NOW.minus(Duration.ofDays(365)), NOW, false); // the full, year-long fetch: about 1 point
        verify(activityWriter).writeCalendar(ID, List.of(OCT_12, OCT_13, OCT_14));
        verify(writer).saveKeepingTexts(eq(ID), any(), eq(result), eq(NOW));
        verifyNoInteractions(ai, rateLimiter);
        verify(usersRepository, never()).claimSync(anyLong(), any(), any());
        verify(heroService).seedIfMissing(ID);
    }

    @Test
    void theDailyRefreshRewritesTheTextsWhenTheDataMoved() {
        AiInputs before = AiInputs.of(result);
        AiInputs fewerCommits = new AiInputs(before.archetype(), before.scores(), 30, before.totalPrs(),
                before.totalStars(), before.topRepos(), before.topLanguages());
        when(writer.storedTexts(ID)).thenReturn(
                new Regeneration.Stored(true, NOW.minus(Duration.ofDays(1)), fewerCommits, false));

        assertThat(service.refreshInsights(ID)).isEqualTo(DailyOutcome.REGENERATED);

        verify(ai).generate("octocat", result);
        verify(writer).save(eq(ID), any(), eq(result), eq(texts), eq(NOW));
    }

    @Test
    void theDailyRefreshCountsAFallbackSeparately() {
        when(ai.generate("octocat", result)).thenReturn(new AiInsights("y", "w", "c", Map.of(),
                new CharacterSheet("t", "l"), Set.of(AiField.WEEKLY_REVIEW)));

        assertThat(service.refreshInsights(ID)).isEqualTo(DailyOutcome.FALLBACK); // no texts yet: written
    }

    @Test
    void theDailyRefreshWithARejectedTokenFlagsTheUser() {
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), any(), eq(false))).thenThrow(new GitHubAuthException("401"));

        assertThat(service.refreshInsights(ID)).isEqualTo(DailyOutcome.NEEDS_REAUTH);
        verify(writer).markNeedsReauth(ID, user.getEncryptedGithubToken());
        verifyNoInteractions(ai, activityWriter);
    }

    @Test
    void theDailyRefreshSkipsAFlaggedUser() {
        user.setNeedsReauth(true);

        assertThat(service.refreshInsights(ID)).isEqualTo(DailyOutcome.NEEDS_REAUTH);
        verifyNoInteractions(gitHub, ai);
    }

    @Test
    void theDailyRefreshLetsOtherGitHubFailuresThroughAndWritesNothing() {
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), any(), eq(false))).thenThrow(new GitHubApiException("HTTP 502"));

        assertThatThrownBy(() -> service.refreshInsights(ID)).isInstanceOf(GitHubApiException.class);
        verifyNoInteractions(activityWriter, ai);
        verify(writer, never()).saveKeepingTexts(anyLong(), any(), any(), any());
    }

    // --- B12: what a GitHub 401 logs (B43) ---

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aRejectedTokenLogsGitHubsRequestIdMessageAndTheTokensAgeButNeverTheToken(CapturedOutput output) {
        user.replaceGithubToken(user.getEncryptedGithubToken(), NOW.minus(Duration.ofHours(26).plusMinutes(5)));
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), any(), eq(false)))
                .thenThrow(new GitHubAuthException("GitHub rejected the token (HTTP 401)", "C0DE:1234:ABCD",
                        "Bad credentials"));
        when(writer.markNeedsReauth(ID, user.getEncryptedGithubToken())).thenReturn(true);

        service.refreshActivity(ID, OCT_13.date());

        assertThat(output).contains("Activity refresh for 'octocat': GitHub rejected the stored token",
                "request id C0DE:1234:ABCD", "message \"Bad credentials\"",
                "token obtained 2026-10-13T09:55:00Z (1d 2h 5m ago)", "marked needs_reauth");
        assertThat(output).doesNotContain("gho_stored").doesNotContain(user.getEncryptedGithubToken());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aLoginThatReplacedTheTokenMeanwhileIsNotUndone(CapturedOutput output) {
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), any(), eq(false))).thenThrow(new GitHubAuthException("401"));
        when(writer.markNeedsReauth(ID, user.getEncryptedGithubToken())).thenReturn(false); // the CAS lost

        assertThat(service.sync(ID)).isEqualTo(SyncOutcome.NEEDS_REAUTH);

        assertThat(output).contains("token obtained unknown (before V8)", "not flagged: a login stored a new token");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void theDailyRefreshGetsTheSame401Handling(CapturedOutput output) {
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), any(), eq(false)))
                .thenThrow(new GitHubAuthException("GitHub rejected the token (HTTP 401)", "C0DE:9", "Bad credentials"));
        when(writer.markNeedsReauth(ID, user.getEncryptedGithubToken())).thenReturn(false); // a login won the race

        assertThat(service.refreshInsights(ID)).isEqualTo(DailyOutcome.NEEDS_REAUTH);

        verify(writer).markNeedsReauth(ID, user.getEncryptedGithubToken()); // the CAS on the token it used (B45)
        assertThat(output).contains("Daily insights refresh for 'octocat': GitHub rejected the stored token",
                "request id C0DE:9", "not flagged: a login stored a new token");
        assertThat(output).doesNotContain("gho_stored");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aSuccessfulDailyRefreshLogsWhichTokenWorked(CapturedOutput output) {
        user.replaceGithubToken(user.getEncryptedGithubToken(), NOW.minus(Duration.ofHours(3)));

        service.refreshInsights(ID);

        assertThat(output).contains("token obtained 2026-10-14T09:00:00Z (3h 0m ago)");
    }

    @Test
    void tokenAges() {
        assertThat(SyncService.age(Duration.ofMinutes(5))).isEqualTo("0h 5m");
        assertThat(SyncService.age(Duration.ofHours(49).plusMinutes(1))).isEqualTo("2d 1h 1m");
        assertThat(SyncService.age(Duration.ofSeconds(-3))).isEqualTo("0m"); // clock skew
    }
}
