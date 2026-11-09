package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiInsightsService;
import com.tushar27x.insightshub.ai.AiProperties;
import com.tushar27x.insightshub.ai.AiTestData;
import com.tushar27x.insightshub.ai.CharacterSheet;
import com.tushar27x.insightshub.ai.Regeneration;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.github.GitHubAuthException;
import com.tushar27x.insightshub.github.GitHubGraphQlClient;
import com.tushar27x.insightshub.github.model.UserInsightsData;
import com.tushar27x.insightshub.hero.HeroService;
import com.tushar27x.insightshub.insights.InsightsEngine;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties.Policy;
import com.tushar27x.insightshub.ratelimit.RateLimiter;
import com.tushar27x.insightshub.ratelimit.RateLimiter.Decision;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.security.TokenCipher;
import com.tushar27x.insightshub.sync.SyncService.PreparedResync;
import com.tushar27x.insightshub.sync.SyncService.SyncOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * B11: full resync semantics. A scope lost, an owed resync ({@code private_resync_due}) or the private switch's
 * resync <b>replaces</b> the stored branch counts instead of only raising them, writes the whole calendar, and (when
 * private is off) clears every other count; activity-only paths flag the owed resync; preparing writes nothing.
 */
class SyncServiceResyncTest {

    private static final long ID = 583231L;
    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");
    private static final String TOKEN = "stored-" + "value"; // not token-shaped (B31)
    private static final String WITH_REPO = "read:user,user:email,repo";
    private static final String WITHOUT_REPO = "read:user,user:email";
    private static final Policy GLOBAL = new Policy(30, Duration.ofMinutes(1));
    private static final Map<LocalDate, Integer> SCAN = Map.of(LocalDate.parse("2026-10-14"), 2);

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
    private SyncService service;
    private Users user;

    @BeforeEach
    void setUp() {
        service = new SyncService(usersRepository, cipher, gitHub, engine, ai, writer, activityWriter, branchScanner,
                JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC),
                new SyncProperties(Duration.ofMinutes(10), 4, 2), rateLimiter,
                new RateLimitProperties(true, Map.of(RateLimitProperties.SYNC_GLOBAL, GLOBAL)), heroService,
                new AiProperties(null, null, Duration.ofSeconds(90), null));
        user = new Users(ID, "octocat");
        user.setEncryptedGithubToken(cipher.encrypt(TOKEN, ID));
        when(usersRepository.findById(ID)).thenReturn(Optional.of(user));
        when(engine.calculate(any(), anyBoolean())).thenReturn(AiTestData.insights());
        when(ai.generate(any(), any())).thenReturn(new AiInsights("y", "w", "c", Map.of(), new CharacterSheet("n", "d")));
        when(branchScanner.scan(any(), any(), any(), any(), anyBoolean())).thenReturn(SCAN);
        when(writer.storedTexts(ID)).thenReturn(Regeneration.Stored.NONE);
    }

    private void fetchReturns(String reportedScopes) {
        when(gitHub.fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), anyBoolean()))
                .thenReturn(new UserInsightsData(null, null, reportedScopes));
    }

    // --- branch counts: reset, not GREATEST ---

    @Test
    void anOrdinarySyncOnlyRaisesBranchCounts() {
        fetchReturns(WITHOUT_REPO);

        service.sync(ID);

        verify(activityWriter).writeBranchCounts(ID, SCAN);
        verify(activityWriter, never()).replaceBranchCounts(anyLong(), anyMap(), anyBoolean());
        verify(activityWriter, never()).writeCalendarInFull(anyLong(), any());
        verify(usersRepository, never()).setPrivateResyncDue(anyLong(), anyBoolean());
    }

    @Test
    void aLostScopeDuringASyncReplacesTheCountsAndClearsTheOthers() {
        user.setIncludePrivate(true);
        user.setGithubScopes(WITH_REPO);
        fetchReturns(WITHOUT_REPO); // GitHub says repo is gone

        service.sync(ID);

        verify(activityWriter).writeCalendarInFull(eq(ID), any());
        verify(activityWriter).replaceBranchCounts(ID, SCAN, true); // private is off now: clear the other days too
        verify(activityWriter, never()).writeBranchCounts(anyLong(), anyMap());
        verify(engine).calculate(any(), eq(false));
        verify(branchScanner).scan(any(), any(), any(), any(), eq(false));
    }

    @Test
    void anOwedResyncMakesTheNextSyncFullAndThenIsPaid() {
        user.setPrivateResyncDue(true);
        fetchReturns(WITHOUT_REPO);

        service.sync(ID);

        verify(activityWriter).writeCalendarInFull(eq(ID), any());
        verify(activityWriter).replaceBranchCounts(ID, SCAN, true);
        verify(usersRepository).setPrivateResyncDue(ID, false);
    }

    @Test
    void anOwedResyncWithPrivateOnReplacesWithoutClearingTheOtherDays() {
        user.setPrivateResyncDue(true);
        user.setIncludePrivate(true);
        user.setGithubScopes(WITH_REPO);
        fetchReturns(WITH_REPO);

        service.sync(ID);

        verify(activityWriter).replaceBranchCounts(ID, SCAN, false);
        verify(engine).calculate(any(), eq(true));
        verify(usersRepository).setPrivateResyncDue(ID, false);
    }

    @Test
    void theDailyInsightsRefreshPaysAnOwedResyncToo() {
        user.setPrivateResyncDue(true);
        fetchReturns(WITHOUT_REPO);

        service.refreshInsights(ID);

        verify(activityWriter).replaceBranchCounts(ID, SCAN, true);
        verify(usersRepository).setPrivateResyncDue(ID, false);
    }

    // --- activity-only paths ---

    @Test
    void anActivityOnlyRefreshThatSeesTheScopeLostFlagsAFullSyncForTheNextRun() {
        user.setIncludePrivate(true);
        user.setGithubScopes(WITH_REPO);
        fetchReturns(WITHOUT_REPO);

        assertThat(service.refreshActivity(ID, LocalDate.parse("2026-10-12"))).isEqualTo(SyncOutcome.SYNCED);

        verify(usersRepository).setPrivateResyncDue(ID, true);
        verify(activityWriter).replaceBranchCounts(ID, SCAN, true); // its own counts are public-only right away
        verifyNoInteractions(ai);
        verify(writer, never()).save(anyLong(), any(), any(), any(), any()); // activity-only: insights wait for the sync
        verify(writer, never()).saveKeepingTexts(anyLong(), any(), any(), any());
    }

    @Test
    void anActivityRefreshWithoutALostScopeFlagsNothing() {
        fetchReturns(WITHOUT_REPO);

        service.refreshActivity(ID, LocalDate.parse("2026-10-12"));

        verify(usersRepository, never()).setPrivateResyncDue(anyLong(), anyBoolean());
        verify(activityWriter).writeBranchCounts(ID, SCAN);
    }

    @Test
    void anActivityRefreshOfAUserWithAnOwedResyncRunsTheFullSyncFirst() {
        user.setPrivateResyncDue(true);
        fetchReturns(WITHOUT_REPO);

        assertThat(service.refreshActivity(ID, LocalDate.parse("2026-10-12"))).isEqualTo(SyncOutcome.SYNCED);

        verify(writer).save(eq(ID), any(), any(), any(), eq(NOW)); // insights rewritten: it was the full sync
        verify(activityWriter).writeCalendarInFull(eq(ID), any());
        verify(usersRepository).setPrivateResyncDue(ID, false);
    }

    // --- the private switch's resync: prepared, nothing written ---

    @Test
    void preparingAResyncFetchesForTheNewValueAndWritesNothing() {
        user.setIncludePrivate(false);
        user.setGithubScopes(WITH_REPO);
        fetchReturns(WITH_REPO);

        PreparedResync prepared = service.prepareResync(ID, true).orElseThrow();

        verify(gitHub).fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), eq(true)); // the target, not the stored value
        verify(branchScanner).scan(any(), any(), any(), any(), eq(true));
        verify(engine).calculate(any(), eq(true));
        assertThat(prepared.includePrivate()).isTrue();
        assertThat(prepared.scopeLost()).isFalse();
        assertThat(prepared.branchCounts()).isEqualTo(SCAN);
        assertThat(prepared.texts()).isNotNull(); // first texts: the LLM ran, outside any transaction
        verifyNoInteractions(activityWriter);
        verify(writer, never()).save(anyLong(), any(), any(), any(), any());
        verify(writer, never()).saveKeepingTexts(anyLong(), any(), any(), any());
        verifyNoInteractions(heroService);
    }

    @Test
    void preparingForOffFetchesPublicOnlyEvenWithTheScope() {
        user.setIncludePrivate(true);
        user.setGithubScopes(WITH_REPO);
        fetchReturns(WITH_REPO);
        when(writer.storedTexts(ID)).thenReturn(new Regeneration.Stored(true, NOW, null, false));

        PreparedResync prepared = service.prepareResync(ID, false).orElseThrow();

        verify(gitHub).fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), eq(false));
        verify(engine).calculate(any(), eq(false));
        assertThat(prepared.includePrivate()).isFalse();
        assertThat(prepared.texts()).isNull(); // stored texts are kept
        verifyNoInteractions(ai);
    }

    @Test
    void preparingNoticesATokenThatLostRepo() {
        user.setGithubScopes(WITH_REPO);
        fetchReturns(WITHOUT_REPO);

        PreparedResync prepared = service.prepareResync(ID, true).orElseThrow();

        assertThat(prepared.includePrivate()).isFalse();
        assertThat(prepared.scopeLost()).isTrue();
        verify(usersRepository).updateGithubScopes(ID, WITHOUT_REPO);
    }

    @Test
    void preparingWithADeadTokenIsEmptyAndWritesNothing() {
        user.setGithubScopes(WITH_REPO);
        when(gitHub.fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), anyBoolean()))
                .thenThrow(new GitHubAuthException("rejected", null, null));
        when(writer.markNeedsReauth(eq(ID), anyString())).thenReturn(true);

        assertThat(service.prepareResync(ID, true)).isEmpty();

        verifyNoInteractions(activityWriter);
        verify(writer, never()).save(anyLong(), any(), any(), any(), any());
    }

    @Test
    void preparingForAUserWhoMustLogInAgainIsEmptyWithoutCallingGitHub() {
        user.setNeedsReauth(true);

        assertThat(service.prepareResync(ID, true)).isEmpty();

        verifyNoInteractions(gitHub);
    }

    @Test
    void aGitHubFailureWhilePreparingPropagatesAndWritesNothing() {
        user.setGithubScopes(WITH_REPO);
        when(gitHub.fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), anyBoolean()))
                .thenThrow(new GitHubApiException("HTTP 502"));

        assertThatThrownBy(() -> service.prepareResync(ID, true)).isInstanceOf(GitHubApiException.class);

        verifyNoInteractions(activityWriter, ai);
    }

    // --- the global cap ---

    @Test
    void theGlobalSlotIsTakenFromTheSyncGlobalPolicy() {
        when(rateLimiter.tryAcquire("sync:global", GLOBAL)).thenReturn(new Decision(true, 30, 29, Duration.ofSeconds(50)));
        assertThat(service.acquireGlobalSlot()).isEmpty();

        when(rateLimiter.tryAcquire("sync:global", GLOBAL)).thenReturn(new Decision(false, 30, 0, Duration.ofSeconds(17)));
        assertThat(service.acquireGlobalSlot()).contains(Duration.ofSeconds(17));
    }
}
