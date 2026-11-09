package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiInsightsService;
import com.tushar27x.insightshub.ai.AiProperties;
import com.tushar27x.insightshub.ai.AiTestData;
import com.tushar27x.insightshub.ai.CharacterSheet;
import com.tushar27x.insightshub.ai.Regeneration;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.github.GitHubGraphQlClient;
import com.tushar27x.insightshub.github.GitHubScopeException;
import com.tushar27x.insightshub.github.model.UserInsightsData;
import com.tushar27x.insightshub.hero.HeroService;
import com.tushar27x.insightshub.insights.InsightsEngine;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import com.tushar27x.insightshub.ratelimit.RateLimiter;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.security.TokenCipher;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B10: private repos are fetched only when the user opted in AND the recorded token has {@code repo}; a lost scope
 * downgrades to public data without an error, a {@code needs_reauth} or a failed sync.
 */
class SyncServicePrivateTest {

    private static final long ID = 583231L;
    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");
    private static final String TOKEN = "stored-" + "value"; // not token-shaped (B31)
    private static final String WITH_REPO = "read:user,user:email,repo";
    private static final String WITHOUT_REPO = "read:user,user:email";

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
                new RateLimitProperties(true, Map.of()), heroService,
                new AiProperties(null, null, Duration.ofSeconds(90), null));
        user = new Users(ID, "octocat");
        user.setEncryptedGithubToken(cipher.encrypt(TOKEN, ID));
        when(usersRepository.findById(ID)).thenReturn(Optional.of(user));
        var result = AiTestData.insights();
        when(engine.calculate(any())).thenReturn(result);
        when(ai.generate(any(), any())).thenReturn(new AiInsights("y", "w", "c", Map.of(), new CharacterSheet("n", "d")));
        when(branchScanner.scan(any(), any(), any(), any(), anyBoolean())).thenReturn(Map.of());
        when(writer.storedTexts(ID)).thenReturn(Regeneration.Stored.NONE);
    }

    private void fetchReturns(UserInsightsData data) {
        when(gitHub.fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), anyBoolean())).thenReturn(data);
    }

    @Test
    void thePublicDefaultFetchesNoPrivateData() {
        fetchReturns(new UserInsightsData(null, null));

        service.sync(ID);

        verify(gitHub).fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), eq(false));
        verify(branchScanner).scan(any(), any(), any(), any(), eq(false));
    }

    @Test
    void optedInWithTheScopeFetchesPrivateInBothTheFetchAndTheBranchScan() {
        user.setIncludePrivate(true);
        user.setGithubScopes(WITH_REPO);
        fetchReturns(new UserInsightsData(null, null, "read:user, repo, user:email"));

        service.sync(ID);

        verify(gitHub).fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), eq(true));
        verify(branchScanner).scan(any(), any(), any(), any(), eq(true));
        verify(usersRepository, never()).updateGithubScopes(org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void theScopeAloneIsNotEnoughAfterTheUserTurnedItOff() {
        user.setIncludePrivate(false); // the flip: never fetch private data again
        user.setGithubScopes(WITH_REPO);
        fetchReturns(new UserInsightsData(null, null, "repo,read:user,user:email"));

        service.sync(ID);
        service.refreshActivity(ID, LocalDate.parse("2026-10-12"));
        service.refreshInsights(ID);

        verify(gitHub, times(3)).fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), eq(false));
        verify(gitHub, never()).fetchUserInsights(any(), any(), any(), any(), eq(true));
        verify(branchScanner, never()).scan(any(), any(), any(), any(), eq(true));
    }

    @Test
    void theChoiceAloneIsNotEnoughWithoutTheScope() {
        user.setIncludePrivate(true);
        user.setGithubScopes(WITHOUT_REPO);
        fetchReturns(new UserInsightsData(null, null, WITHOUT_REPO));

        assertThat(service.sync(ID)).isEqualTo(SyncOutcome.SYNCED);

        verify(gitHub, never()).fetchUserInsights(any(), any(), any(), any(), eq(true));
        verify(usersRepository, never()).updateGithubScopes(org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void aTokenThatLostRepoSyncsPublicDataRecordsTheScopesAndIsNotFlagged() {
        user.setIncludePrivate(true);
        user.setGithubScopes(WITH_REPO);
        fetchReturns(new UserInsightsData(null, null, WITHOUT_REPO)); // GitHub's header says repo is gone

        assertThat(service.sync(ID)).isEqualTo(SyncOutcome.SYNCED);

        verify(usersRepository).updateGithubScopes(ID, WITHOUT_REPO);
        verify(branchScanner).scan(any(), any(), any(), any(), eq(false)); // the scan doesn't go private either
        verify(writer, never()).markNeedsReauth(org.mockito.ArgumentMatchers.anyLong(), anyString());
        verify(writer).save(eq(ID), any(), any(), any(), eq(NOW)); // the sync finished
    }

    @Test
    void aRefusedPrivateQueryIsRetriedPublicOnlyWithoutNeedsReauth() {
        user.setIncludePrivate(true);
        user.setGithubScopes(WITH_REPO);
        when(gitHub.fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), eq(true)))
                .thenThrow(new GitHubScopeException("FORBIDDEN"));
        when(gitHub.fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), eq(false)))
                .thenReturn(new UserInsightsData(null, null));

        assertThat(service.sync(ID)).isEqualTo(SyncOutcome.SYNCED);

        verify(usersRepository).updateGithubScopes(ID, WITHOUT_REPO);
        verify(branchScanner).scan(any(), any(), any(), any(), eq(false));
        verify(writer, never()).markNeedsReauth(org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void theHeroJobsActivityRefreshDowngradesTheSameWay() {
        user.setIncludePrivate(true);
        user.setGithubScopes(WITH_REPO);
        when(gitHub.fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), eq(true)))
                .thenThrow(new GitHubScopeException("FORBIDDEN"));
        when(gitHub.fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), eq(false)))
                .thenReturn(new UserInsightsData(null, null));

        assertThat(service.refreshActivity(ID, LocalDate.parse("2026-10-12"))).isEqualTo(SyncOutcome.SYNCED);

        verify(usersRepository).updateGithubScopes(ID, WITHOUT_REPO);
        verify(writer, never()).markNeedsReauth(org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void aScopeRefusalOnAPublicFetchIsNotSwallowed() {
        fetchReturns(null);
        when(gitHub.fetchUserInsights(eq(TOKEN), eq("octocat"), any(), any(), eq(false)))
                .thenThrow(new GitHubScopeException("FORBIDDEN"));

        assertThatThrownBy(() -> service.sync(ID)).isInstanceOf(GitHubApiException.class);
        verify(usersRepository, never()).updateGithubScopes(org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void aTokenThatGainedRepoElsewhereHasItsScopesRecordedButNothingPrivateIsReadUntilOptIn() {
        user.setGithubScopes(WITHOUT_REPO);
        fetchReturns(new UserInsightsData(null, null, WITH_REPO));

        service.sync(ID);

        verify(usersRepository).updateGithubScopes(ID, WITH_REPO);
        verify(gitHub, never()).fetchUserInsights(any(), any(), any(), any(), eq(true));
    }
}
