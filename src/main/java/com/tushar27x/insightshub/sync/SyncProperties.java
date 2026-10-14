package com.tushar27x.insightshub.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code insightshub.sync.*}.
 *
 * @param loginStaleAfter at login, data younger than this is reused instead of re-synced (v1: 7 days).
 *                        A sync costs a GitHub query and five LLM calls; Phase 10's daily job keeps data fresh.
 */
@ConfigurationProperties(prefix = "insightshub.sync")
public record SyncProperties(@DefaultValue("7d") Duration loginStaleAfter) {
}
