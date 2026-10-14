package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiInsightsService;
import com.tushar27x.insightshub.ai.AiTestData;
import com.tushar27x.insightshub.ai.CharacterSheet;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.github.GitHubAuthException;
import com.tushar27x.insightshub.github.GitHubGraphQlClient;
import com.tushar27x.insightshub.github.model.UserInsightsData;
import com.tushar27x.insightshub.insights.InsightsEngine;
import com.tushar27x.insightshub.insights.InsightsResult;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.security.TokenCipher;
import com.tushar27x.insightshub.sync.SyncService.SyncOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
    private final InsightsResult result = AiTestData.insights();
    private final AiInsights texts = new AiInsights("year", "week", "craft", Map.of("app", "An app."),
            new CharacterSheet("octocat the Monk", "Calm."));

    private SyncService service;
    private Users user;

    @BeforeEach
    void setUp() {
        service = new SyncService(usersRepository, cipher, gitHub, engine, ai, writer, JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC), new SyncProperties(COOLDOWN));
        user = new Users(ID, "octocat");
        user.setEncryptedGithubToken(cipher.encrypt("gho_stored", ID));
        when(usersRepository.findById(ID)).thenReturn(Optional.of(user));

        UserInsightsData data = new UserInsightsData(null, null);
        when(gitHub.fetchUserInsights("gho_stored", "octocat")).thenReturn(data);
        when(engine.calculate(any())).thenReturn(result);
        when(ai.generate("octocat", result)).thenReturn(texts);
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
    void claimedSyncRuns() {
        when(usersRepository.claimSync(ID, NOW, NOW.minus(COOLDOWN))).thenReturn(1);

        assertThat(service.syncIfDue(ID)).isEqualTo(SyncOutcome.SYNCED);
        verify(writer).save(eq(ID), any(), eq(result), eq(texts), eq(NOW));
    }

    @Test
    void syncDuringCooldownFetchesNothing() {
        when(usersRepository.claimSync(ID, NOW, NOW.minus(COOLDOWN))).thenReturn(0);

        assertThat(service.syncIfDue(ID)).isEqualTo(SyncOutcome.COOLDOWN);
        verifyNoInteractions(gitHub, ai, writer);
    }

    @Test
    void nextSyncIsTenMinutesAfterTheLastStart() {
        user.setSyncRequestedAt(NOW.minus(Duration.ofMinutes(4)));
        assertThat(service.nextSyncAllowedAt(ID)).isEqualTo(NOW.plus(Duration.ofMinutes(6)));

        user.setSyncRequestedAt(NOW.minus(Duration.ofMinutes(20)));
        assertThat(service.nextSyncAllowedAt(ID)).isEqualTo(NOW);

        user.setSyncRequestedAt(null);
        assertThat(service.nextSyncAllowedAt(ID)).isEqualTo(NOW);
    }

    @Test
    void rejectedTokenFlagsTheUserInsteadOfFailing() {
        when(gitHub.fetchUserInsights("gho_stored", "octocat")).thenThrow(new GitHubAuthException("401"));

        assertThat(service.sync(ID)).isEqualTo(SyncOutcome.NEEDS_REAUTH);
        verify(writer).markNeedsReauth(ID);
        verify(writer, never()).save(anyLong(), any(), any(), any(), any());
        verifyNoInteractions(ai);
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
        verifyNoInteractions(writer);
    }

    @Test
    void unknownUserIsAnError() {
        assertThatThrownBy(() -> service.sync(42L)).isInstanceOf(IllegalArgumentException.class);
    }
}
