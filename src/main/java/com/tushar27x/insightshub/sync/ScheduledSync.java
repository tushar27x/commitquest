package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.sync.SyncService.SyncOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 10: refreshes every user's activity once a day, so the hero's history keeps up even for users who
 * don't open the app. Cron in {@code insightshub.sync.cron} (UTC; {@code "-"} turns it off).
 * <p>
 * Activity only ({@link SyncService#refreshActivity}): the contribution calendar for the last
 * {@link ActivityWriter#OVERLAP_DAYS} days and the branch scan. No insights and no AI: the texts are
 * regenerated only by a sync the user starts (login or refresh). It doesn't claim the per-user cooldown either,
 * so it never blocks a refresh; its writes are idempotent upserts.
 * <ul>
 *   <li>One virtual thread per user, at most {@code insightshub.sync.concurrency} refreshing at once.</li>
 *   <li>Each user is isolated: a failure is logged and counted, and the others go on.</li>
 *   <li>GitHub 401 → {@code needs_reauth}, done by {@link SyncService}; those users drop out of later runs.</li>
 * </ul>
 * Spring runs a scheduled method on one thread and never starts it again while it runs, so runs can't
 * overlap within an instance. With several instances, each would refresh everyone: harmless, but duplicate
 * work that a lock (ShedLock) would avoid.
 */
@Slf4j
@Component
public class ScheduledSync {

    private final UsersRepository usersRepository;
    private final SyncService syncService;
    private final Clock clock;
    private final int concurrency;

    public ScheduledSync(UsersRepository usersRepository, SyncService syncService, SyncProperties properties,
                         Clock clock) {
        this.usersRepository = usersRepository;
        this.syncService = syncService;
        this.clock = clock;
        this.concurrency = properties.concurrency();
    }

    @Scheduled(cron = "${insightshub.sync.cron}", zone = "UTC")
    public void syncAll() {
        run();
    }

    /** One run over all users; returns once every user is done. */
    Summary run() {
        long start = System.nanoTime();
        List<Long> ids = usersRepository.findIdsForScheduledSync();
        // refreshActivity adds a day of slack, so this covers the overlap in every zone
        LocalDate from = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(ActivityWriter.OVERLAP_DAYS);
        log.info("Scheduled activity refresh started: {} users from {}, {} at a time", ids.size(), from, concurrency);

        Semaphore permits = new Semaphore(concurrency);
        Counts counts = new Counts();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (long id : ids) {
                executor.submit(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        counts.add(syncService.refreshActivity(id, from));
                    } catch (GitHubApiException e) {
                        counts.failed.incrementAndGet();
                        log.warn("Scheduled activity refresh for user {} failed: {}", id, e.getMessage());
                    } catch (RuntimeException e) {
                        counts.failed.incrementAndGet();
                        log.error("Scheduled activity refresh for user {} failed", id, e);
                    } finally {
                        permits.release();
                    }
                });
            }
        } // close() waits for every task

        Summary summary = counts.summary();
        log.info("Scheduled activity refresh done in {} s: {}", (System.nanoTime() - start) / 1_000_000_000, summary);
        return summary;
    }

    record Summary(int synced, int needsReauth, int failed) {
    }

    private static final class Counts {
        final AtomicInteger synced = new AtomicInteger();
        final AtomicInteger needsReauth = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();

        void add(SyncOutcome outcome) {
            switch (outcome) {
                case SYNCED -> synced.incrementAndGet();
                case NEEDS_REAUTH -> needsReauth.incrementAndGet();
                // refreshActivity never returns these: it takes no cooldown and no global cap
                case COOLDOWN, BUSY -> throw new IllegalStateException("Unexpected outcome " + outcome);
            }
        }

        Summary summary() {
            return new Summary(synced.get(), needsReauth.get(), failed.get());
        }
    }
}
