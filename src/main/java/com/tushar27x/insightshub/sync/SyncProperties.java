package com.tushar27x.insightshub.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code insightshub.sync.*}.
 *
 * @param minInterval per-user cooldown between syncs the user starts (login or refresh). GitHub's limit is
 *                    per user token, so it isn't the constraint; each sync also makes five LLM calls on
 *                    the app's shared key.
 */
@ConfigurationProperties(prefix = "insightshub.sync")
public record SyncProperties(@DefaultValue("10m") Duration minInterval) {
}
