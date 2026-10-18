package com.tushar27x.insightshub.hero;

import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.hero.HeroService.DueHero;
import com.tushar27x.insightshub.sync.SyncService;
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
 * Phase 11, the hourly hero job (plan §3): for every hero with a day due (06:00 local the next morning, see
 * {@link HeroRules#dueDays}), refresh that user's activity from GitHub, then settle the due days in order.
 * Cron in {@code insightshub.hero.cron} (UTC; {@code "-"} turns it off).
 * <ul>
 *   <li>The refresh comes first because the daily sync runs at one UTC hour, which is mid-day somewhere: for
 *       most zones, the last hours of the day being settled would otherwise be missing.</li>
 *   <li>No refresh, no settling: if GitHub is down the days wait for a later run (catch-up settles them in
 *       order), and a user whose token GitHub rejected waits until they log in again instead of losing a
 *       heart a day for activity we can't see.</li>
 *   <li>Each user is isolated, on a virtual thread, at most {@code insightshub.hero.concurrency} at once.</li>
 *   <li>Idempotent: {@link HeroWriter#apply} only moves a hero that's still where this run found it, so a
 *       re-run, a retry or a second instance settles nothing twice.</li>
 * </ul>
 */
@Slf4j
@Component
public class HeroJob {

    private final HeroService heroService;
    private final SyncService syncService;
    private final int concurrency;

    public HeroJob(HeroService heroService, SyncService syncService, HeroProperties properties) {
        this.heroService = heroService;
        this.syncService = syncService;
        this.concurrency = properties.concurrency();
    }

    @Scheduled(cron = "${insightshub.hero.cron}", zone = "UTC")
    public void evaluateAll() {
        run();
    }

    /** One run over the due heroes; returns once every one is done. */
    Summary run() {
        long start = System.nanoTime();
        List<DueHero> due = heroService.dueHeroes();
        if (due.isEmpty()) {
            log.debug("Hero job: nothing due");
            return new Summary(0, 0, 0, 0, 0);
        }
        log.info("Hero job started: {} heroes due, {} at a time", due.size(), concurrency);

        Semaphore permits = new Semaphore(concurrency);
        Counts counts = new Counts();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (DueHero hero : due) {
                executor.submit(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        settle(hero, counts);
                    } finally {
                        permits.release();
                    }
                });
            }
        } // close() waits for every task

        Summary summary = counts.summary();
        log.info("Hero job done in {} ms: {}", (System.nanoTime() - start) / 1_000_000, summary);
        return summary;
    }

    private void settle(DueHero hero, Counts counts) {
        long id = hero.userId();
        try {
            if (syncService.refreshActivity(id, hero.firstDue()) == SyncOutcome.NEEDS_REAUTH) {
                counts.waitingForReauth.incrementAndGet();
                return;
            }
            int days = heroService.evaluate(id);
            if (days > 0) {
                counts.heroes.incrementAndGet();
                counts.days.addAndGet(days);
            }
        } catch (GitHubApiException e) {
            counts.deferred.incrementAndGet();
            log.warn("Hero job: activity refresh for user {} failed, its days wait for the next run: {}", id, e.getMessage());
        } catch (RuntimeException e) {
            counts.failed.incrementAndGet();
            log.error("Hero job failed for user {}", id, e);
        }
    }

    /**
     * @param heroes           heroes that settled at least one day
     * @param days             days settled in total
     * @param waitingForReauth skipped: GitHub rejects the user's token
     * @param deferred         skipped: GitHub failed, retried next run
     */
    record Summary(int heroes, int days, int waitingForReauth, int deferred, int failed) {
    }

    private static final class Counts {
        final AtomicInteger heroes = new AtomicInteger();
        final AtomicInteger days = new AtomicInteger();
        final AtomicInteger waitingForReauth = new AtomicInteger();
        final AtomicInteger deferred = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();

        Summary summary() {
            return new Summary(heroes.get(), days.get(), waitingForReauth.get(), deferred.get(), failed.get());
        }
    }
}
