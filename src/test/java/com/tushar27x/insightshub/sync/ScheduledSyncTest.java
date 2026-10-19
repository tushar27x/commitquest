package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInsightsService;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.github.GitHubGraphQlClient;
import com.tushar27x.insightshub.github.model.GitHubUser;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionCalendar;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionsCollection;
import com.tushar27x.insightshub.github.model.GitHubUser.Week;
import com.tushar27x.insightshub.github.model.UserInsightsData;
import com.tushar27x.insightshub.hero.HeroService;
import com.tushar27x.insightshub.insights.InsightsEngine;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import com.tushar27x.insightshub.ratelimit.RateLimiter;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.security.TokenCipher;
import com.tushar27x.insightshub.sync.ScheduledSync.Summary;
import com.tushar27x.insightshub.sync.SyncService.SyncOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

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

class ScheduledSyncTest {

    /** 03:00 UTC, the daily cron. */
    private static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");
    /** A week back, {@link ActivityWriter#OVERLAP_DAYS}; refreshActivity adds a day of slack for zones. */
    private static final LocalDate FROM = LocalDate.parse("2026-10-12");

    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final SyncService syncService = mock(SyncService.class);

    private ScheduledSync job(int concurrency) {
        return job(syncService, concurrency);
    }

    private ScheduledSync job(SyncService service, int concurrency) {
        return new ScheduledSync(usersRepository, service, new SyncProperties(Duration.ofMinutes(10), concurrency, 2),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void refreshesEveryUsersLastWeekAndCountsTheOutcomes() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(List.of(1L, 2L, 3L));
        when(syncService.refreshActivity(1L, FROM)).thenReturn(SyncOutcome.SYNCED);
        when(syncService.refreshActivity(2L, FROM)).thenReturn(SyncOutcome.SYNCED);
        when(syncService.refreshActivity(3L, FROM)).thenReturn(SyncOutcome.NEEDS_REAUTH);

        assertThat(job(2).run()).isEqualTo(new Summary(2, 1, 0));
    }

    @Test
    void oneUsersFailureDoesntStopTheOthers() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(List.of(1L, 2L, 3L));
        when(syncService.refreshActivity(1L, FROM)).thenThrow(new GitHubApiException("HTTP 502"));
        when(syncService.refreshActivity(2L, FROM)).thenThrow(new IllegalStateException("bug"));
        when(syncService.refreshActivity(3L, FROM)).thenReturn(SyncOutcome.SYNCED);

        assertThat(job(1).run()).isEqualTo(new Summary(1, 0, 2));
        verify(syncService).refreshActivity(3L, FROM);
    }

    @Test
    void neverRunsTheFullSync() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(List.of(1L));
        when(syncService.refreshActivity(1L, FROM)).thenReturn(SyncOutcome.SYNCED);

        job(1).run();

        verify(syncService, never()).sync(anyLong());
        verify(syncService, never()).syncIfDue(anyLong());
    }

    /**
     * With a real {@link SyncService}: the daily run writes activity only. No AI, no insights engine, no insights
     * row (so its {@code updated_at} stays), no hero seeding, and no cooldown claim that would block a refresh.
     */
    @Test
    void theDailyRunNeverCallsTheAiOrTouchesTheInsights() {
        long id = 583231L;
        TokenCipher cipher = new TokenCipher(SecurityTestData.appProperties());
        Users user = new Users(id, "octocat");
        user.setEncryptedGithubToken(cipher.encrypt("stored-token", id));
        when(usersRepository.findById(id)).thenReturn(Optional.of(user));
        when(usersRepository.findIdsForScheduledSync()).thenReturn(List.of(id));

        ContributionDay oct18 = new ContributionDay(LocalDate.parse("2026-10-18"), 2);
        GitHubUser gitHubUser = new GitHubUser("U_octocat", "octocat", null, null, null, null, null, null, null, null,
                new ContributionsCollection(List.of(), new ContributionCalendar(2, List.of(new Week(List.of(oct18)))),
                        2, 0, 0, 0));
        GitHubGraphQlClient gitHub = mock(GitHubGraphQlClient.class);
        when(gitHub.fetchUserInsights("stored-token", "octocat", Instant.parse("2026-10-11T00:00:00Z"), NOW))
                .thenReturn(new UserInsightsData(null, gitHubUser));
        BranchScanner branchScanner = mock(BranchScanner.class);
        when(branchScanner.scan(any(), any(), any(), any())).thenReturn(Map.of());
        InsightsEngine engine = mock(InsightsEngine.class);
        AiInsightsService ai = mock(AiInsightsService.class);
        InsightsWriter insightsWriter = mock(InsightsWriter.class);
        ActivityWriter activityWriter = mock(ActivityWriter.class);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        HeroService heroService = mock(HeroService.class);
        SyncService real = new SyncService(usersRepository, cipher, gitHub, engine, ai, insightsWriter,
                activityWriter, branchScanner, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC),
                new SyncProperties(Duration.ofMinutes(10), 4, 2), rateLimiter,
                new RateLimitProperties(true, Map.of()), heroService);

        assertThat(job(real, 1).run()).isEqualTo(new Summary(1, 0, 0));

        verify(activityWriter).writeCalendar(id, List.of(oct18));
        verifyNoInteractions(ai, engine, insightsWriter, rateLimiter, heroService);
        verify(gitHub, never()).fetchUserInsights(any(), any()); // the full, year-long fetch
        verify(usersRepository, never()).claimSync(anyLong(), any(), any());
    }

    @Test
    void noUsersIsAQuietRun() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(List.of());

        assertThat(job(4).run()).isEqualTo(new Summary(0, 0, 0));
        verifyNoInteractions(syncService);
    }

    @Test
    @Timeout(10)
    void neverMoreThanTheConfiguredNumberAtOnce() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(LongStream.rangeClosed(1, 30).boxed().toList());
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        when(syncService.refreshActivity(anyLong(), eq(FROM))).thenAnswer(call -> {
            peak.accumulateAndGet(running.incrementAndGet(), Math::max);
            Thread.sleep(20);
            running.decrementAndGet();
            return SyncOutcome.SYNCED;
        });

        assertThat(job(3).run().synced()).isEqualTo(30);
        assertThat(peak.get()).isBetween(2, 3); // 3 in practice; 2 only if a scheduler stall serialises them
    }

    @Test
    void concurrencyMustBePositive() {
        assertThatThrownBy(() -> job(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
