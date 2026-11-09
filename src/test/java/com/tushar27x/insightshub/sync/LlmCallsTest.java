package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInputs;
import com.tushar27x.insightshub.ai.AiInsightsService;
import com.tushar27x.insightshub.ai.AiProperties;
import com.tushar27x.insightshub.ai.AiReviewer;
import com.tushar27x.insightshub.ai.AiTestData;
import com.tushar27x.insightshub.ai.FakeChatModel;
import com.tushar27x.insightshub.ai.Regeneration;
import com.tushar27x.insightshub.entity.Users;
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
import com.tushar27x.insightshub.sync.SyncService.SyncOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B11: how many LLM calls each path makes, counted on a {@link FakeChatModel} behind the real {@link SyncService},
 * {@link AiInsightsService}, {@link AiReviewer} and chain. A new user's first sync makes exactly one; every later
 * login or SAVE ({@code syncIfDue}, the same path) and the hero job's activity refresh make none.
 */
class LlmCallsTest {

    private static final long ID = 583231L;
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final Policy GLOBAL = new Policy(30, Duration.ofMinutes(1));

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final InsightsWriter writer = mock(InsightsWriter.class);
    private final Users user = new Users(ID, "octocat");

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    private SyncService service(FakeChatModel model) {
        TokenCipher cipher = new TokenCipher(SecurityTestData.appProperties());
        user.setEncryptedGithubToken(cipher.encrypt("stored-token", ID));
        when(usersRepository.findById(ID)).thenReturn(Optional.of(user));
        when(usersRepository.claimSync(anyLong(), any(), any())).thenReturn(1);
        GitHubGraphQlClient gitHub = mock(GitHubGraphQlClient.class);
        when(gitHub.fetchUserInsights(any(), any(), any(), any(), anyBoolean())).thenReturn(new UserInsightsData(null, null));
        when(gitHub.fetchUserInsights(any(), any(), any(), any(), anyBoolean())).thenReturn(new UserInsightsData(null, null));
        InsightsEngine engine = mock(InsightsEngine.class);
        when(engine.calculate(any())).thenReturn(AiTestData.insights());
        BranchScanner branchScanner = mock(BranchScanner.class);
        when(branchScanner.scan(any(), any(), any(), any(), anyBoolean())).thenReturn(Map.of());
        RateLimiter rateLimiter = mock(RateLimiter.class);
        when(rateLimiter.tryAcquire("sync:global", GLOBAL)).thenReturn(new Decision(true, 30, 29, Duration.ZERO));

        AiProperties aiProperties = new AiProperties(null, null, Duration.ofSeconds(5), null);
        AiInsightsService ai = new AiInsightsService(new AiReviewer(FakeChatModel.chain(model)), executor, aiProperties);
        return new SyncService(usersRepository, cipher, gitHub, engine, ai, writer, mock(ActivityWriter.class),
                branchScanner, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC),
                new SyncProperties(Duration.ofMinutes(10), 4, 2), rateLimiter,
                new RateLimitProperties(true, Map.of(RateLimitProperties.SYNC_GLOBAL, GLOBAL)), mock(HeroService.class),
                aiProperties);
    }

    @Test
    void aNewUsersFirstLoginMakesExactlyOneCall() {
        FakeChatModel model = FakeChatModel.answering("{\"yearInReview\": \"Welcome.\"}");
        SyncService service = service(model);
        when(writer.storedTexts(ID)).thenReturn(Regeneration.Stored.NONE);

        assertThat(service.syncIfDue(ID).outcome()).isEqualTo(SyncOutcome.SYNCED);

        assertThat(model.calls()).isEqualTo(1);
    }

    @Test
    void laterLoginsAndSaveMakeNone() {
        FakeChatModel model = FakeChatModel.mustNotBeCalled();
        SyncService service = service(model);
        when(writer.storedTexts(ID)).thenReturn(new Regeneration.Stored(true, NOW.minus(Duration.ofDays(30)),
                AiInputs.of(AiTestData.insights()), true)); // even stale fallback texts: that's the daily job's work

        for (int i = 0; i < 3; i++) {
            assertThat(service.syncIfDue(ID).outcome()).isEqualTo(SyncOutcome.SYNCED);
        }
        service.sync(ID);

        assertThat(model.calls()).isZero();
    }

    @Test
    void theHeroJobsActivityRefreshMakesNone() {
        FakeChatModel model = FakeChatModel.mustNotBeCalled();
        SyncService service = service(model);

        service.refreshActivity(ID, LocalDate.parse("2026-11-04"));

        assertThat(model.calls()).isZero();
    }
}
