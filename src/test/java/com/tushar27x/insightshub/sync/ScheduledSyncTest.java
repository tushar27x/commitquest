package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInputs;
import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiInsightsService;
import com.tushar27x.insightshub.ai.AiProperties;
import com.tushar27x.insightshub.ai.AiTestData;
import com.tushar27x.insightshub.ai.CharacterSheet;
import com.tushar27x.insightshub.ai.Regeneration;
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
import com.tushar27x.insightshub.insights.InsightsResult;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import com.tushar27x.insightshub.ratelimit.RateLimiter;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.security.TokenCipher;
import com.tushar27x.insightshub.sync.ScheduledSync.Summary;
import com.tushar27x.insightshub.sync.SyncService.DailyOutcome;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScheduledSyncTest {

    /** 03:00 UTC, the daily cron. */
    private static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");

    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final SyncService syncService = mock(SyncService.class);

    private ScheduledSync job(int concurrency) {
        return job(syncService, concurrency);
    }

    private ScheduledSync job(SyncService service, int concurrency) {
        return new ScheduledSync(usersRepository, service, new SyncProperties(Duration.ofMinutes(10), concurrency, 2));
    }

    @Test
    void refreshesEveryUserAndCountsTheOutcomes() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(List.of(1L, 2L, 3L, 4L, 5L));
        when(syncService.refreshInsights(1L)).thenReturn(DailyOutcome.REGENERATED);
        when(syncService.refreshInsights(2L)).thenReturn(DailyOutcome.UNCHANGED);
        when(syncService.refreshInsights(3L)).thenReturn(DailyOutcome.UNCHANGED);
        when(syncService.refreshInsights(4L)).thenReturn(DailyOutcome.FALLBACK);
        when(syncService.refreshInsights(5L)).thenReturn(DailyOutcome.NEEDS_REAUTH);

        assertThat(job(2).run()).isEqualTo(new Summary(5, 1, 2, 1, 1, 0));
    }

    @Test
    void oneUsersFailureDoesntStopTheOthers() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(List.of(1L, 2L, 3L));
        when(syncService.refreshInsights(1L)).thenThrow(new GitHubApiException("HTTP 502"));
        when(syncService.refreshInsights(2L)).thenThrow(new IllegalStateException("bug"));
        when(syncService.refreshInsights(3L)).thenReturn(DailyOutcome.UNCHANGED);

        assertThat(job(1).run()).isEqualTo(new Summary(3, 0, 1, 0, 0, 2));
        verify(syncService).refreshInsights(3L);
    }

    @Test
    void neverRunsTheUserSyncOrTheActivityOnlyRefresh() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(List.of(1L));
        when(syncService.refreshInsights(1L)).thenReturn(DailyOutcome.UNCHANGED);

        job(1).run();

        verify(syncService, never()).sync(anyLong());
        verify(syncService, never()).syncIfDue(anyLong());
        verify(syncService, never()).refreshActivity(anyLong(), any());
    }

    @Test
    void noUsersIsAQuietRun() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(List.of());

        assertThat(job(4).run()).isEqualTo(new Summary(0, 0, 0, 0, 0, 0));
        verifyNoInteractions(syncService);
    }

    @Test
    @Timeout(10)
    void neverMoreThanTheConfiguredNumberAtOnce() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(LongStream.rangeClosed(1, 30).boxed().toList());
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        when(syncService.refreshInsights(anyLong())).thenAnswer(call -> {
            peak.accumulateAndGet(running.incrementAndGet(), Math::max);
            Thread.sleep(20);
            running.decrementAndGet();
            return DailyOutcome.UNCHANGED;
        });

        assertThat(job(3).run().unchanged()).isEqualTo(30);
        assertThat(peak.get()).isBetween(2, 3); // 3 in practice; 2 only if a scheduler stall serialises them
    }

    @Test
    void concurrencyMustBePositive() {
        assertThatThrownBy(() -> job(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- With a real SyncService (B11) ---

    /** A {@link SyncService} with mocks around it; {@link Real#writer} remembers what was saved, like the table. */
    private final class Real {
        final TokenCipher cipher = new TokenCipher(SecurityTestData.appProperties());
        final GitHubGraphQlClient gitHub = mock(GitHubGraphQlClient.class);
        final InsightsEngine engine = mock(InsightsEngine.class);
        final AiInsightsService ai = mock(AiInsightsService.class);
        final InsightsWriter writer = mock(InsightsWriter.class);
        final ActivityWriter activityWriter = mock(ActivityWriter.class);
        final RateLimiter rateLimiter = mock(RateLimiter.class);
        final HeroService heroService = mock(HeroService.class);
        final AtomicReference<Instant> clock = new AtomicReference<>(NOW);
        /** What the insights row holds about each user's texts. */
        final Map<Long, Regeneration.Stored> stored = new java.util.concurrent.ConcurrentHashMap<>();
        final SyncService service;

        Real(int llmConcurrency, long... ids) {
            BranchScanner branchScanner = mock(BranchScanner.class);
            when(branchScanner.scan(any(), any(), any(), any(), anyBoolean())).thenReturn(Map.of());
            for (long id : ids) {
                Users user = new Users(id, "user" + id);
                user.setEncryptedGithubToken(cipher.encrypt("stored-token", id));
                when(usersRepository.findById(id)).thenReturn(Optional.of(user));
                ContributionDay oct18 = new ContributionDay(LocalDate.parse("2026-10-18"), 2);
                GitHubUser gitHubUser = new GitHubUser("U_" + id, "user" + id, null, null, null, null, null, null,
                        null, null, new ContributionsCollection(List.of(),
                        new ContributionCalendar(2, List.of(new Week(List.of(oct18)))), 2, 0, 0, 0));
                when(gitHub.fetchUserInsights(eq("stored-token"), eq("user" + id), any(), any(), eq(false)))
                        .thenReturn(new UserInsightsData(null, gitHubUser));
                when(writer.storedTexts(id)).thenAnswer(call -> stored.getOrDefault(id, Regeneration.Stored.NONE));
            }
            when(usersRepository.findIdsForScheduledSync()).thenReturn(LongStream.of(ids).boxed().toList());
            when(engine.calculate(any())).thenReturn(AiTestData.insights());
            when(ai.generate(any(), any())).thenReturn(new AiInsights("y", "w", "c", Map.of("app", "An app."),
                    new CharacterSheet("t the Monk", "l")));
            doAnswer(call -> {
                InsightsResult result = call.getArgument(2);
                AiInsights texts = call.getArgument(3);
                stored.put(call.getArgument(0), new Regeneration.Stored(true, call.getArgument(4), AiInputs.of(result),
                        texts.fallback()));
                return null;
            }).when(writer).save(anyLong(), any(), any(), any(), any());
            Clock ticking = new Clock() {
                @Override
                public ZoneOffset getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(java.time.ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return clock.get();
                }
            };
            service = new SyncService(usersRepository, cipher, gitHub, engine, ai, writer, activityWriter,
                    branchScanner, JsonMapper.builder().build(), ticking, new SyncProperties(Duration.ofMinutes(10), 4, 2),
                    rateLimiter, new RateLimitProperties(true, Map.of()), heroService,
                    new AiProperties(null, null, Duration.ofSeconds(90), null,
                            new AiProperties.Regenerate(5, 0.10, Duration.ofDays(7), llmConcurrency)));
        }
    }

    @Test
    void theDailyRunWritesInsightsButCallsTheLlmOnlyWhenTheDataMovedAndAtMostOnceADay() {
        long id = 583231L;
        Real real = new Real(2, id);
        real.stored.put(id, new Regeneration.Stored(true, NOW.minus(Duration.ofDays(1)),
                AiInputs.of(AiTestData.insights()), false));

        assertThat(job(real.service, 1).run()).isEqualTo(new Summary(1, 0, 1, 0, 0, 0)); // nothing moved
        verify(real.gitHub).fetchUserInsights(eq("stored-token"), eq("user583231"), any(), any(), eq(false)); // the full insights fetch
        verify(real.writer).saveKeepingTexts(eq(583231L), any(), any(), eq(NOW));
        verifyNoInteractions(real.ai, real.rateLimiter);
        verify(usersRepository, never()).claimSync(anyLong(), any(), any());

        // the class changed since: rewritten once ...
        AiInputs monk = AiInputs.of(AiTestData.insights());
        real.stored.put(id, new Regeneration.Stored(true, NOW.minus(Duration.ofDays(1)), new AiInputs(
                com.tushar27x.insightshub.entity.Archetype.ROGUE, monk.scores(), monk.totalCommits(), monk.totalPrs(),
                monk.totalStars(), monk.topRepos(), monk.topLanguages()), false));
        assertThat(job(real.service, 1).run().regenerated()).isEqualTo(1);
        // ... and a second run the same day doesn't call again, even with a fallback stored
        real.stored.put(id, new Regeneration.Stored(true, NOW, real.stored.get(id).inputs(), true));
        real.clock.set(NOW.plus(Duration.ofHours(2)));
        assertThat(job(real.service, 1).run().unchanged()).isEqualTo(1);
        verify(real.ai).generate(any(), any());
    }

    @Test
    void aWeekOldTextIsRewrittenThoughNothingChanged() {
        Real real = new Real(2, 583231L);
        real.stored.put(583231L, new Regeneration.Stored(true, NOW.minus(Duration.ofDays(7)),
                AiInputs.of(AiTestData.insights()), false));

        assertThat(job(real.service, 1).run().regenerated()).isEqualTo(1);
    }

    @Test
    @Timeout(10)
    void llmCallsHaveTheirOwnLowerCap() {
        long[] ids = LongStream.rangeClosed(1, 12).toArray();
        Real real = new Real(2, ids); // no texts yet: every user is rewritten
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        when(real.ai.generate(any(), any())).thenAnswer(call -> {
            peak.accumulateAndGet(running.incrementAndGet(), Math::max);
            Thread.sleep(30);
            running.decrementAndGet();
            return new AiInsights("y", "w", "c", Map.of(), new CharacterSheet("t", "l"));
        });

        assertThat(job(real.service, 6).run().regenerated()).isEqualTo(12);
        assertThat(peak.get()).isBetween(1, 2);
    }
}
