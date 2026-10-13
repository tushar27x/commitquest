package com.tushar27x.insightshub.ai;

import com.tushar27x.insightshub.entity.Archetype;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.tushar27x.insightshub.ai.AiTestData.insights;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiInsightsServiceTest {

    private static final CharacterSheet SHEET = new CharacterSheet("tushar27x the Monk", "lore");

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AiReviewer reviewer = mock(AiReviewer.class);

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    private AiInsightsService service(Duration taskTimeout) {
        return new AiInsightsService(reviewer, executor, new AiProperties(null, null, taskTimeout, null));
    }

    /** Every generator answers after {@code delayMs}. */
    private void answerAll(long delayMs) {
        when(reviewer.roast(any(), any())).thenAnswer(delayed(delayMs, "roast"));
        when(reviewer.weeklyReview(any())).thenAnswer(delayed(delayMs, "weekly"));
        when(reviewer.craftReview(any())).thenAnswer(delayed(delayMs, "craft"));
        when(reviewer.repoSummaries(anyList())).thenAnswer(delayed(delayMs, Map.of("app", "An app.")));
        when(reviewer.characterSheet(anyString(), any(), any(), anyList())).thenAnswer(delayed(delayMs, SHEET));
    }

    private static <T> Answer<T> delayed(long delayMs, T value) {
        return invocation -> {
            Thread.sleep(delayMs);
            return value;
        };
    }

    @Test
    void collectsEveryGeneratorsAnswer() {
        answerAll(0);

        AiInsights ai = service(Duration.ofSeconds(5)).generate("tushar27x", insights());

        assertThat(ai).isEqualTo(new AiInsights("roast", "weekly", "craft", Map.of("app", "An app."), SHEET));
    }

    @Test
    void generatorsRunInParallel() {
        answerAll(400);

        long start = System.nanoTime();
        service(Duration.ofSeconds(5)).generate("tushar27x", insights());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // 5 x 400 ms would take 2 s one after another
        assertThat(elapsedMs).isLessThan(1_200);
    }

    @Test
    void aCrashingGeneratorOnlyLosesItsOwnResult() {
        answerAll(0);
        when(reviewer.roast(any(), any())).thenThrow(new IllegalStateException("bug"));
        when(reviewer.characterSheet(anyString(), any(), any(), anyList())).thenThrow(new IllegalStateException("bug"));

        AiInsights ai = service(Duration.ofSeconds(5)).generate("tushar27x", insights());

        assertThat(ai.roast()).isEqualTo(AiInsightsService.ROAST_PENDING);   // v1's route-level fallback
        assertThat(ai.characterSheet()).isEqualTo(AiReviewer.fallbackSheet("tushar27x", Archetype.MONK));
        assertThat(ai.weeklyReview()).isEqualTo("weekly");
        assertThat(ai.repoSummaries()).containsKey("app");
    }

    @Test
    void slowGeneratorsAreCutOffAtTheTaskTimeout() {
        answerAll(2_000);

        long start = System.nanoTime();
        AiInsights ai = service(Duration.ofMillis(150)).generate("tushar27x", insights());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(1_500);
        assertThat(ai.roast()).isEqualTo(AiInsightsService.ROAST_PENDING);
        assertThat(ai.weeklyReview()).isEqualTo(AiInsightsService.WEEKLY_PENDING);
        assertThat(ai.craftReview()).isEqualTo(AiInsightsService.CRAFT_PENDING);
        assertThat(ai.repoSummaries()).isEmpty();
        assertThat(ai.characterSheet().title()).isEqualTo("tushar27x the Monk");
    }
}
