package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiInsightsService;
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
import com.tushar27x.insightshub.insights.InsightsResult;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.security.TokenCipher;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties.Policy;
import com.tushar27x.insightshub.ratelimit.RateLimiter;
import com.tushar27x.insightshub.ratelimit.RateLimiter.Decision;
import com.tushar27x.insightshub.sync.SyncService.SyncOutcome;
import com.tushar27x.insightshub.sync.SyncService.SyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
                new RateLimitProperties(true, Map.of(RateLimitProperties.SYNC_GLOBAL, GLOBAL)), heroService);
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
        when(gitHub.fetchUserInsights("gho_stored", "octocat")).thenReturn(data);
        when(engine.calculate(any())).thenReturn(result);
        when(ai.generate("octocat", result)).thenReturn(texts);
        when(branchScanner.scan(any(), any(), any(), any())).thenReturn(Map.of());
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
        when(branchScanner.scan("gho_stored", "U_octocat", calendar, ZoneOffset.UTC))
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
        when(gitHub.fetchUserInsights("gho_stored", "octocat")).thenReturn(new UserInsightsData(null, null));

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
        when(gitHub.fetchUserInsights("gho_stored", "octocat")).thenThrow(new GitHubAuthException("401"));

        assertThat(service.sync(ID)).isEqualTo(SyncOutcome.NEEDS_REAUTH);
        verify(writer).markNeedsReauth(ID);
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
        when(gitHub.fetchUserInsights("gho_stored", "octocat")).thenThrow(new GitHubApiException("HTTP 502"));

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

        verify(branchScanner).scan("gho_stored", "U_octocat", List.of(OCT_12, OCT_13, OCT_14), ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void refreshWritesTheCalendarFromADayBeforeTheFirstDueDayWithoutAi() {
        UserInsightsData data = new UserInsightsData(null, new GitHubUser("U_octocat", "octocat", null, null, null, null,
                null, null, null, null, new ContributionsCollection(List.of(),
                new ContributionCalendar(1, List.of(new Week(List.of(OCT_13)))), 1, 0, 0, 0)));
        when(gitHub.fetchUserInsights("gho_stored", "octocat", Instant.parse("2026-10-12T00:00:00Z"), NOW))
                .thenReturn(data);
        when(branchScanner.scan("gho_stored", "U_octocat", List.of(OCT_13), ZoneOffset.UTC))
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
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), eq(NOW)))
                .thenReturn(new UserInsightsData(null, null));

        service.refreshActivity(ID, LocalDate.parse("2020-01-01"));

        verify(gitHub).fetchUserInsights("gho_stored", "octocat", NOW.minus(Duration.ofDays(365)), NOW);
    }

    @Test
    void refreshWithARejectedTokenFlagsTheUser() {
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), any()))
                .thenThrow(new GitHubAuthException("401"));

        assertThat(service.refreshActivity(ID, OCT_13.date())).isEqualTo(SyncOutcome.NEEDS_REAUTH);
        verify(writer).markNeedsReauth(ID);
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
        when(gitHub.fetchUserInsights(eq("gho_stored"), eq("octocat"), any(), any()))
                .thenThrow(new GitHubApiException("HTTP 502"));

        assertThatThrownBy(() -> service.refreshActivity(ID, OCT_13.date())).isInstanceOf(GitHubApiException.class);
        verifyNoInteractions(activityWriter);
    }
}
