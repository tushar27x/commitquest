package com.tushar27x.insightshub.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code insightshub.sync.*}. The daily sync's schedule, {@code insightshub.sync.cron}, is read by
 * {@code @Scheduled} in {@link ScheduledSync} directly.
 *
 * @param minInterval    per-user cooldown between syncs the user starts (login or refresh). GitHub's limit is
 *                       per user token, so it isn't the constraint; since B11 only a new user's first sync calls
 *                       the LLM. The daily refresh neither checks nor claims it.
 * @param concurrency    how many users the daily insights refresh runs at once (GitHub on each user's own token +
 *                       DB writes); its LLM calls have their own, lower cap ({@code insightshub.ai.regenerate.concurrency})
 * @param branchScanDays how many days, up to today, the branch scan checks for commits the calendar misses
 */
@ConfigurationProperties(prefix = "insightshub.sync")
public record SyncProperties(@DefaultValue("10m") Duration minInterval,
                             @DefaultValue("4") int concurrency,
                             @DefaultValue("2") int branchScanDays) {

    public SyncProperties {
        if (concurrency < 1) {
            throw new IllegalArgumentException("insightshub.sync.concurrency must be at least 1");
        }
        if (branchScanDays < 0) {
            throw new IllegalArgumentException("insightshub.sync.branch-scan-days must not be negative");
        }
    }
}
