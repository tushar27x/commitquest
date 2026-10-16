package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.sync.ScheduledSync.Summary;
import com.tushar27x.insightshub.sync.SyncService.SyncOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScheduledSyncTest {

    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final SyncService syncService = mock(SyncService.class);

    private ScheduledSync job(int concurrency) {
        return new ScheduledSync(usersRepository, syncService, new SyncProperties(Duration.ofMinutes(10), concurrency, 2));
    }

    @Test
    void syncsEveryUserAndCountsTheOutcomes() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(syncService.scheduledSync(1L)).thenReturn(SyncOutcome.SYNCED);
        when(syncService.scheduledSync(2L)).thenReturn(SyncOutcome.SYNCED);
        when(syncService.scheduledSync(3L)).thenReturn(SyncOutcome.COOLDOWN);
        when(syncService.scheduledSync(4L)).thenReturn(SyncOutcome.NEEDS_REAUTH);

        assertThat(job(2).run()).isEqualTo(new Summary(2, 1, 1, 0));
    }

    @Test
    void oneUsersFailureDoesntStopTheOthers() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(List.of(1L, 2L, 3L));
        when(syncService.scheduledSync(1L)).thenThrow(new GitHubApiException("HTTP 502"));
        when(syncService.scheduledSync(2L)).thenThrow(new IllegalStateException("bug"));
        when(syncService.scheduledSync(3L)).thenReturn(SyncOutcome.SYNCED);

        assertThat(job(1).run()).isEqualTo(new Summary(1, 0, 0, 2));
        verify(syncService).scheduledSync(3L);
    }

    @Test
    void noUsersIsAQuietRun() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(List.of());

        assertThat(job(4).run()).isEqualTo(new Summary(0, 0, 0, 0));
        verifyNoInteractions(syncService);
    }

    @Test
    @Timeout(10)
    void neverMoreThanTheConfiguredNumberAtOnce() {
        when(usersRepository.findIdsForScheduledSync()).thenReturn(LongStream.rangeClosed(1, 30).boxed().toList());
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        when(syncService.scheduledSync(anyLong())).thenAnswer(call -> {
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
