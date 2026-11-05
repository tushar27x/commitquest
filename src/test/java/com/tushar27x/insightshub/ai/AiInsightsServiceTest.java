package com.tushar27x.insightshub.ai;

import com.tushar27x.insightshub.insights.InsightsResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.tushar27x.insightshub.ai.AiTestData.insights;
import static org.assertj.core.api.Assertions.assertThat;

/** B11: one combined call per user, bounded by one timeout. */
class AiInsightsServiceTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    private AiInsightsService service(FakeChatModel model, Duration timeout) {
        return new AiInsightsService(new AiReviewer(FakeChatModel.chain(model)), executor,
                new AiProperties(null, null, timeout, null));
    }

    @Test
    void exactlyOneLlmCallPerUser() {
        FakeChatModel model = FakeChatModel.answering(AiReviewerTest.ANSWER);

        AiInsights texts = service(model, Duration.ofSeconds(5)).generate("tushar27x", insights());

        assertThat(model.prompts).hasSize(1);
        assertThat(texts.fallbacks()).isEmpty();
        assertThat(texts.yearInReview()).isEqualTo("Great year.");
    }

    @Test
    void aSlowCallIsCutOffAtTheTimeoutAndEveryFieldFallsBack() {
        FakeChatModel slow = FakeChatModel.answering(prompt -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return AiReviewerTest.ANSWER;
        });

        long start = System.nanoTime();
        AiInsights texts = service(slow, Duration.ofMillis(150)).generate("tushar27x", insights());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(1_500);
        assertThat(texts).isEqualTo(AiReviewer.fallbacks("tushar27x", insights()));
    }

    @Test
    void aCrashIsEveryFieldsFallbackNotAnException() {
        AiReviewer crashing = new AiReviewer(null) {
            @Override
            public AiInsights review(String login, InsightsResult insights) {
                throw new IllegalStateException("bug");
            }
        };
        AiInsightsService service = new AiInsightsService(crashing, executor,
                new AiProperties(null, null, Duration.ofSeconds(5), null));

        AiInsights texts = service.generate("tushar27x", insights());

        assertThat(texts.fallbacks()).containsExactlyInAnyOrder(AiField.values());
    }
}
