package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.sync.SyncService.DailyOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 10, reworked in B11: refreshes every user once a day, so the hero's history and the insights keep up even
 * for users who don't open the app. Cron in {@code insightshub.sync.cron} (UTC; {@code "-"} turns it off).
 * <p>
 * A full insights refresh per user ({@link SyncService#refreshInsights}): one GitHub insights fetch on the user's own
 * token, the activity, stats and scores written, and the AI texts rewritten only when the data moved since they were
 * written (at most once per user per day). It doesn't claim the per-user cooldown, so it never blocks a refresh; its
 * writes are idempotent upserts.
 * <ul>
 *   <li>One virtual thread per user, at most {@code insightshub.sync.concurrency} refreshing at once, and at most
 *       {@code insightshub.ai.regenerate.concurrency} of them calling the LLM.</li>
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
        log.info("Scheduled insights refresh started: {} users, {} at a time", ids.size(), concurrency);

        Semaphore permits = new Semaphore(concurrency);
        Counts counts = new Counts();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (long id : ids) {
                executor.submit(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        counts.add(syncService.refreshInsights(id));
                    } catch (GitHubApiException e) {
                        counts.failed.incrementAndGet();
                        log.warn("Scheduled insights refresh for user {} failed: {}", id, e.getMessage());
                    } catch (RuntimeException e) {
                        counts.failed.incrementAndGet();
                        log.error("Scheduled insights refresh for user {} failed", id, e);
                    } finally {
                        permits.release();
                    }
                });
            }
        } // close() waits for every task

        Summary summary = counts.summary(ids.size());
        log.info("Scheduled insights refresh done in {} s: {}", (System.nanoTime() - start) / 1_000_000_000, summary);
        return summary;
    }

    /**
     * @param regenerated texts rewritten, every field from the LLM
     * @param unchanged   texts kept: nothing moved enough, or already written today
     * @param fallbacks   texts rewritten with at least one static fallback (retried next run)
     */
    record Summary(int users, int regenerated, int unchanged, int fallbacks, int needsReauth, int failed) {
    }

    private static final class Counts {
        final AtomicInteger regenerated = new AtomicInteger();
        final AtomicInteger unchanged = new AtomicInteger();
        final AtomicInteger fallbacks = new AtomicInteger();
        final AtomicInteger needsReauth = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();

        void add(DailyOutcome outcome) {
            switch (outcome) {
                case REGENERATED -> regenerated.incrementAndGet();
                case UNCHANGED -> unchanged.incrementAndGet();
                case FALLBACK -> fallbacks.incrementAndGet();
                case NEEDS_REAUTH -> needsReauth.incrementAndGet();
            }
        }

        Summary summary(int users) {
            return new Summary(users, regenerated.get(), unchanged.get(), fallbacks.get(), needsReauth.get(),
                    failed.get());
        }
    }
}
