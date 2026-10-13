package com.tushar27x.insightshub.ai;

import com.tushar27x.insightshub.insights.InsightsResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Runs all AI generators for one user in parallel (v1: {@code asyncio.gather(..., return_exceptions=True)}).
 * Each task fails on its own: a timeout or an unexpected exception yields that task's fallback,
 * and the other tasks are unaffected.
 */
@Slf4j
@Service
public class AiInsightsService {

    static final String YEAR_IN_REVIEW_PENDING = "Your year in review is on its way.";
    static final String WEEKLY_PENDING = "Activity analysis in progress.";
    static final String CRAFT_PENDING = "Code quality audit scheduled.";

    private final AiReviewer reviewer;
    private final Executor executor;
    private final Duration taskTimeout;

    /**
     * @param executor Boot's {@code applicationTaskExecutor}: virtual threads with
     *                 {@code spring.threads.virtual.enabled}, ideal for tasks that mostly wait on HTTP
     */
    public AiInsightsService(AiReviewer reviewer, @Qualifier("applicationTaskExecutor") Executor executor,
                             AiProperties properties) {
        this.reviewer = reviewer;
        this.executor = executor;
        this.taskTimeout = properties.taskTimeout();
    }

    public AiInsights generate(String login, InsightsResult insights) {
        long start = System.nanoTime();
        var stats = insights.stats();

        var yearInReview = async("year in review", () -> reviewer.yearInReview(stats, insights.archetype()),
                YEAR_IN_REVIEW_PENDING);
        var weekly = async("weekly review", () -> reviewer.weeklyReview(stats), WEEKLY_PENDING);
        var craft = async("craft review", () -> reviewer.craftReview(stats), CRAFT_PENDING);
        var summaries = async("repo summaries", () -> reviewer.repoSummaries(stats.repoDetails()),
                Map.<String, String>of());
        var sheet = async("character sheet", () -> reviewer.characterSheet(login, insights.archetypeScores(),
                insights.habits(), stats.topLanguages()), AiReviewer.fallbackSheet(login, insights.archetype()));

        CompletableFuture.allOf(yearInReview, weekly, craft, summaries, sheet).join();
        log.info("AI insights for '{}' generated in {} ms", login, (System.nanoTime() - start) / 1_000_000);

        return new AiInsights(yearInReview.join(), weekly.join(), craft.join(), summaries.join(), sheet.join());
    }

    private <T> CompletableFuture<T> async(String task, Supplier<T> generator, T fallback) {
        return CompletableFuture.supplyAsync(generator, executor)
                .completeOnTimeout(fallback, taskTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(e -> {
                    log.error("AI task '{}' crashed; using its fallback", task, e);
                    return fallback;
                });
    }
}
