package com.tushar27x.insightshub.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code insightshub.sync.*}. The daily sync's schedule, {@code insightshub.sync.cron}, is read by
 * {@code @Scheduled} in {@link ScheduledSync} directly.
 *
 * @param minInterval    per-user cooldown between syncs the user starts (login or refresh). GitHub's limit is
 *                       per user token, so it isn't the constraint; each sync also makes five LLM calls on the
 *                       app's shared key. The daily activity refresh neither checks nor claims it.
 * @param concurrency    how many users the daily activity refresh runs at once. It makes no LLM calls, so this
 *                       bounds the database pool, not GitHub (each user has their own budget).
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
