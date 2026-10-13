package com.tushar27x.insightshub.ai;

import com.tushar27x.insightshub.github.model.GitHubUser;
import com.tushar27x.insightshub.insights.InsightsEngine;
import com.tushar27x.insightshub.insights.InsightsResult;
import com.tushar27x.insightshub.insights.ScoringArchetypeClassifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Calls the real Groq API with the v1 parity fixture. Skipped unless GROQ_API_KEY is set:
 * <pre>GROQ_API_KEY=... ./mvnw test -Dtest=AiLiveTest</pre>
 * Uses about 7 Groq requests.
 */
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
class AiLiveTest {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1";
    private static final String MODEL = "openai/gpt-oss-120b";
    private static final String FAST_MODEL = "openai/gpt-oss-20b";

    private final String key = System.getenv("GROQ_API_KEY");
    private final ClassPathResource persona = new ClassPathResource("prompts/persona.st");
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    private LlmProvider groq(String name, String apiKey) {
        return new LlmProvider(name,
                AiConfig.client(AiConfig.openAiCompatibleModel(GROQ_URL, apiKey, MODEL), persona), MODEL, FAST_MODEL, "low");
    }

    private static InsightsResult fixtureInsights() throws IOException {
        try (InputStream in = new ClassPathResource("insights/v1-parity-user.json").getInputStream()) {
            GitHubUser user = JsonMapper.builder().build().readValue(in, GitHubUser.class);
            return new InsightsEngine(new ScoringArchetypeClassifier()).calculate(user);
        }
    }

    @Test
    void generatesEverythingAgainstGroqInParallel() throws IOException {
        AiInsightsService service = new AiInsightsService(new AiReviewer(new LlmChain(List.of(groq("groq", key)))),
                executor, new AiProperties(FAST_MODEL, "low", Duration.ofSeconds(90), null));
        InsightsResult insights = fixtureInsights();

        long start = System.nanoTime();
        AiInsights ai = service.generate("octocat", insights);
        System.out.printf("archetype: %s%nyear in review: %s%nweekly: %s%ncraft: %s%nsummaries: %s%nsheet: %s%ntook %d ms%n",
                insights.archetype(), ai.yearInReview(), ai.weeklyReview(), ai.craftReview(), ai.repoSummaries(),
                ai.characterSheet(), (System.nanoTime() - start) / 1_000_000);

        assertThat(List.of(ai.yearInReview(), ai.weeklyReview(), ai.craftReview()))
                .as("no static fallbacks").doesNotContain(AiReviewer.PROVIDERS_DOWN);
        assertThat(ai.repoSummaries().values()).isNotEmpty().doesNotContain(AiReviewer.DEFAULT_REPO_SUMMARY);
        assertThat(ai.characterSheet()).isNotEqualTo(AiReviewer.fallbackSheet("octocat", insights.archetype()));
    }

    @Test
    void deadPrimaryKeyFallsBackToTheSecondProvider() throws IOException {
        // the Java version of v1's test_ai.py: kill the primary key, the fallback must answer
        LlmChain chain = new LlmChain(List.of(groq("dead-primary", "gsk_invalid"), groq("fallback", key)));

        String review = new AiReviewer(chain).yearInReview(fixtureInsights().stats(), fixtureInsights().archetype());
        System.out.println("year in review via fallback: " + review);

        assertThat(review).isNotEqualTo(AiReviewer.PROVIDERS_DOWN);
    }
}
