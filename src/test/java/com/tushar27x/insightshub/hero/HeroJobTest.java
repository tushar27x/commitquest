package com.tushar27x.insightshub.hero;

import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.hero.HeroJob.Summary;
import com.tushar27x.insightshub.hero.HeroService.DueHero;
import com.tushar27x.insightshub.sync.SyncService;
import com.tushar27x.insightshub.sync.SyncService.SyncOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The hourly hero job: refresh the due users' activity, then settle their days, each user on their own. */
class HeroJobTest {

    private static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);

    private final HeroService heroService = mock(HeroService.class);
    private final SyncService syncService = mock(SyncService.class);

    private HeroJob job(int concurrency) {
        return new HeroJob(heroService, syncService, new HeroProperties(concurrency, 40, Duration.ofDays(7)));
    }

    private static List<DueHero> due(long... ids) {
        return LongStream.of(ids).mapToObj(id -> new DueHero(id, OCT_15)).toList();
    }

    @Test
    void refreshesTheActivityBeforeSettlingTheDays() {
        when(heroService.dueHeroes()).thenReturn(due(1L));
        when(syncService.refreshActivity(1L, OCT_15)).thenReturn(SyncOutcome.SYNCED);
        when(heroService.evaluate(1L)).thenReturn(1);

        assertThat(job(2).run()).isEqualTo(new Summary(1, 1, 0, 0, 0));
        var order = inOrder(syncService, heroService);
        order.verify(syncService).refreshActivity(1L, OCT_15);
        order.verify(heroService).evaluate(1L);
    }

    @Test
    void countsTheOutcomes() {
        when(heroService.dueHeroes()).thenReturn(due(1L, 2L, 3L, 4L));
        when(syncService.refreshActivity(anyLong(), any())).thenReturn(SyncOutcome.SYNCED);
        when(syncService.refreshActivity(4L, OCT_15)).thenReturn(SyncOutcome.NEEDS_REAUTH);
        when(heroService.evaluate(1L)).thenReturn(3); // a 3-day catch-up
        when(heroService.evaluate(2L)).thenReturn(1);
        when(heroService.evaluate(3L)).thenReturn(0); // another instance got there first

        assertThat(job(2).run()).isEqualTo(new Summary(2, 4, 1, 0, 0));
    }

    @Test
    void aUserWhoseTokenWasRevokedWaitsInsteadOfLosingHearts() {
        when(heroService.dueHeroes()).thenReturn(due(1L));
        when(syncService.refreshActivity(1L, OCT_15)).thenReturn(SyncOutcome.NEEDS_REAUTH);

        assertThat(job(1).run().waitingForReauth()).isEqualTo(1);
        verify(heroService, never()).evaluate(1L);
    }

    @Test
    void whenGitHubIsDownTheDaysWaitForTheNextRun() {
        when(heroService.dueHeroes()).thenReturn(due(1L, 2L));
        when(syncService.refreshActivity(1L, OCT_15)).thenThrow(new GitHubApiException("HTTP 502"));
        when(syncService.refreshActivity(2L, OCT_15)).thenReturn(SyncOutcome.SYNCED);
        when(heroService.evaluate(2L)).thenReturn(1);

        assertThat(job(1).run()).isEqualTo(new Summary(1, 1, 0, 1, 0));
        verify(heroService, never()).evaluate(1L);
    }

    @Test
    void oneUsersFailureDoesntStopTheOthers() {
        when(heroService.dueHeroes()).thenReturn(due(1L, 2L));
        when(syncService.refreshActivity(anyLong(), any())).thenReturn(SyncOutcome.SYNCED);
        when(heroService.evaluate(1L)).thenThrow(new IllegalStateException("bug"));
        when(heroService.evaluate(2L)).thenReturn(1);

        assertThat(job(1).run()).isEqualTo(new Summary(1, 1, 0, 0, 1));
    }

    @Test
    void noDueHeroesIsAQuietRun() {
        when(heroService.dueHeroes()).thenReturn(List.of());

        assertThat(job(4).run()).isEqualTo(new Summary(0, 0, 0, 0, 0));
        verifyNoInteractions(syncService);
    }

    @Test
    @Timeout(10)
    void neverMoreThanTheConfiguredNumberAtOnce() {
        when(heroService.dueHeroes()).thenReturn(due(LongStream.rangeClosed(1, 20).toArray()));
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        when(syncService.refreshActivity(anyLong(), any())).thenAnswer(call -> {
            peak.accumulateAndGet(running.incrementAndGet(), Math::max);
            Thread.sleep(20);
            running.decrementAndGet();
            return SyncOutcome.SYNCED;
        });
        when(heroService.evaluate(anyLong())).thenReturn(1);

        assertThat(job(3).run().heroes()).isEqualTo(20);
        assertThat(peak.get()).isBetween(2, 3);
    }

    @Test
    void concurrencyMustBePositive() {
        assertThatThrownBy(() -> job(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
