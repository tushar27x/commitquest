package com.tushar27x.insightshub.ai;

import com.tushar27x.insightshub.insights.InsightsResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Writes one user's AI texts with the one combined call ({@link AiReviewer#review}, B11), bounded by
 * {@code insightshub.ai.task-timeout}. A timeout or an unexpected exception yields every field's fallback; the
 * caller never waits longer than the timeout and never sees an exception.
 * <p>
 * Called only by a new user's first sync and by the daily job when the data moved ({@code SyncService}).
 */
@Slf4j
@Service
public class AiInsightsService {

    private final AiReviewer reviewer;
    private final Executor executor;
    private final Duration taskTimeout;

    /**
     * @param executor Boot's {@code applicationTaskExecutor}: virtual threads with
     *                 {@code spring.threads.virtual.enabled}, ideal for a task that mostly waits on HTTP
     */
    public AiInsightsService(AiReviewer reviewer, @Qualifier("applicationTaskExecutor") Executor executor,
                             AiProperties properties) {
        this.reviewer = reviewer;
        this.executor = executor;
        this.taskTimeout = properties.taskTimeout();
    }

    public AiInsights generate(String login, InsightsResult insights) {
        long start = System.nanoTime();
        AiInsights texts = CompletableFuture.supplyAsync(() -> reviewer.review(login, insights), executor)
                .completeOnTimeout(null, taskTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(e -> {
                    log.error("AI texts for '{}' crashed; using every fallback", login, e);
                    return AiReviewer.fallbacks(login, insights);
                })
                .join();
        if (texts == null) {
            log.warn("AI texts for '{}' timed out after {}; using every fallback", login, taskTimeout);
            texts = AiReviewer.fallbacks(login, insights);
        }
        log.info("AI texts for '{}' generated in {} ms, fallbacks {}", login, (System.nanoTime() - start) / 1_000_000,
                texts.fallbacks().isEmpty() ? "none" : texts.fallbacks());
        return texts;
    }
}
