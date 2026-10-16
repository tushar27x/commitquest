package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.sync.SyncService.SyncOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 10: syncs every user with a usable token once a day, so the hero's activity history keeps up
 * even for users who don't open the app. Cron in {@code insightshub.sync.cron} (UTC; {@code "-"} turns it off).
 * <ul>
 *   <li>One virtual thread per user, at most {@code insightshub.sync.concurrency} syncing at once.</li>
 *   <li>Each user is isolated: a failure is logged and counted, and the others go on.</li>
 *   <li>GitHub 401 → {@code needs_reauth}, done by {@link SyncService}; those users drop out of later runs.</li>
 * </ul>
 * Spring runs a scheduled method on one thread and never starts it again while it runs, so runs can't
 * overlap within an instance. With several instances, each would sync everyone; the cooldown claim makes
 * the second sync of a user a no-op, but a lock (ShedLock) would be needed to avoid the duplicate work.
 */
@Slf4j
@Component
public class ScheduledSync {

    private final UsersRepository usersRepository;
    private final SyncService syncService;
    private final int concurrency;

    public ScheduledSync(UsersRepository usersRepository, SyncService syncService, SyncProperties properties) {
        this.usersRepository = usersRepository;
        this.syncService = syncService;
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
        log.info("Scheduled sync started: {} users, {} at a time", ids.size(), concurrency);

        Semaphore permits = new Semaphore(concurrency);
        Counts counts = new Counts();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (long id : ids) {
                executor.submit(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        counts.add(syncService.scheduledSync(id));
                    } catch (GitHubApiException e) {
                        counts.failed.incrementAndGet();
                        log.warn("Scheduled sync for user {} failed: {}", id, e.getMessage());
                    } catch (RuntimeException e) {
                        counts.failed.incrementAndGet();
                        log.error("Scheduled sync for user {} failed", id, e);
                    } finally {
                        permits.release();
                    }
                });
            }
        } // close() waits for every task

        Summary summary = counts.summary();
        log.info("Scheduled sync done in {} s: {}", (System.nanoTime() - start) / 1_000_000_000, summary);
        return summary;
    }

    /** {@code skipped}: synced less than {@code min-interval} ago (usually by the user). */
    record Summary(int synced, int skipped, int needsReauth, int failed) {
    }

    private static final class Counts {
        final AtomicInteger synced = new AtomicInteger();
        final AtomicInteger skipped = new AtomicInteger();
        final AtomicInteger needsReauth = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();

        void add(SyncOutcome outcome) {
            switch (outcome) {
                case SYNCED -> synced.incrementAndGet();
                case COOLDOWN, BUSY -> skipped.incrementAndGet();
                case NEEDS_REAUTH -> needsReauth.incrementAndGet();
            }
        }

        Summary summary() {
            return new Summary(synced.get(), skipped.get(), needsReauth.get(), failed.get());
        }
    }
}
