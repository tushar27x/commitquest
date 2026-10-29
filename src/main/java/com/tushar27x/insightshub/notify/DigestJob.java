package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.notify.DigestService.Result;
import com.tushar27x.insightshub.repository.DigestRepository.Candidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 12, the hourly digest job, at {@code insightshub.digest.cron} (UTC; 35 past, half an hour after the hero
 * job settles the days). Each user with unsent {@code hero_events} gets at most one email per local day, from
 * {@code insightshub.digest.send-hour} local: see {@link DigestPlan} for who and what, {@link DigestService} for
 * the delivery guarantee.
 * <ul>
 *   <li>Off unless {@code insightshub.digest.enabled}: the app boots and runs without SMTP.</li>
 *   <li>Each user is isolated, on a virtual thread, at most {@code insightshub.digest.concurrency} at once.</li>
 *   <li>The run summary counts the LLM calls: this is the only scheduled LLM use (#32).</li>
 * </ul>
 */
@Slf4j
@Component
public class DigestJob {

    private final DigestService digestService;
    private final Clock clock;
    private final boolean enabled;
    private final int concurrency;

    public DigestJob(DigestService digestService, Clock clock, DigestProperties properties,
                     @Value("${spring.mail.host:}") String mailHost) {
        if (properties.enabled() && mailHost.isBlank()) {
            throw new IllegalStateException("insightshub.digest.enabled needs spring.mail.host (MAIL_HOST)");
        }
        this.digestService = digestService;
        this.clock = clock;
        this.enabled = properties.enabled();
        this.concurrency = properties.concurrency();
        if (!enabled) {
            log.info("Digest disabled (insightshub.digest.enabled=false): no digest emails are sent");
        }
    }

    @Scheduled(cron = "${insightshub.digest.cron}", zone = "UTC")
    public void sendAll() {
        if (enabled) {
            run();
        }
    }

    /** One run over the users with unsent events; returns once every one is done. */
    public Summary run() {
        long start = System.nanoTime();
        Instant now = clock.instant();
        List<Candidate> candidates = digestService.candidates();
        if (candidates.isEmpty()) {
            log.debug("Digest job: no unsent events");
            return new Summary(0, 0, 0, 0, 0, 0);
        }

        Semaphore permits = new Semaphore(concurrency);
        Counts counts = new Counts();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Candidate user : candidates) {
                executor.submit(() -> {
                    permits.acquireUninterruptibly();
                    try {
                        process(user, now, counts);
                    } finally {
                        permits.release();
                    }
                });
            }
        } // close() waits for every task

        Summary summary = counts.summary();
        if (summary.sent() + summary.skipped() + summary.failed() > 0) {
            log.info("Digest job done in {} ms: {}", (System.nanoTime() - start) / 1_000_000, summary);
        }
        return summary;
    }

    private void process(Candidate user, Instant now, Counts counts) {
        try {
            Result result = digestService.process(user, now);
            switch (result.outcome()) {
                case SENT -> counts.sent.incrementAndGet();
                case SKIPPED -> counts.skipped.incrementAndGet();
                case FAILED -> counts.failed.incrementAndGet();
                case NOT_DUE -> counts.notDue.incrementAndGet();
            }
            if (result.introAsked()) {
                counts.aiCalls.incrementAndGet();
                if (!result.introFromAi()) {
                    counts.aiFallbacks.incrementAndGet();
                }
            }
        } catch (RuntimeException e) {
            counts.failed.incrementAndGet();
            log.error("Digest job failed for user {}", user.userId(), e);
        }
    }

    /**
     * @param sent        digests delivered and recorded
     * @param skipped     users whose unsent events were stamped without an email
     * @param notDue      users with unsent events, before their send hour or already mailed today
     * @param failed      not delivered or not recorded; retried next run
     * @param aiCalls     intros asked of the LLM (one per send attempt; a provider fallback isn't counted twice)
     * @param aiFallbacks of those, how many used the static intro
     */
    public record Summary(int sent, int skipped, int notDue, int failed, int aiCalls, int aiFallbacks) {
    }

    private static final class Counts {
        final AtomicInteger sent = new AtomicInteger();
        final AtomicInteger skipped = new AtomicInteger();
        final AtomicInteger notDue = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final AtomicInteger aiCalls = new AtomicInteger();
        final AtomicInteger aiFallbacks = new AtomicInteger();

        Summary summary() {
            return new Summary(sent.get(), skipped.get(), notDue.get(), failed.get(), aiCalls.get(), aiFallbacks.get());
        }
    }
}
