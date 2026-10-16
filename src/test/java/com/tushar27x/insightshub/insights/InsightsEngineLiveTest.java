package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.github.GitHubGraphQlClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs a real GitHub profile through the engine and prints the habit signals and scores, to check that
 * the scoring floors and caps make sense on real data. Skipped unless GITHUB_TOKEN is set:
 * <pre>GITHUB_TOKEN=... [GITHUB_LOGIN=...] ./mvnw test -Dtest=InsightsEngineLiveTest</pre>
 */
@EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
class InsightsEngineLiveTest {

    private final GitHubGraphQlClient client = new GitHubGraphQlClient(
            RestClient.builder().baseUrl("https://api.github.com").defaultHeader("User-Agent", "insightshub").build(),
            new ClassPathResource("graphql/user-insights.graphql"),
            new ClassPathResource("graphql/branch-activity.graphql"),
            Clock.systemUTC());

    @Test
    void scoresARealProfile() {
        String login = System.getenv().getOrDefault("GITHUB_LOGIN", "tushar27x");
        var user = client.fetchUserInsights(System.getenv("GITHUB_TOKEN"), login).user();

        InsightsResult result = new InsightsEngine(new ScoringArchetypeClassifier()).calculate(user);

        System.out.println("habits: " + result.habits());
        System.out.println("scores: " + result.archetypeScores().scores() + " -> " + result.archetype());
        assertThat(result.archetypeScores().scores()).hasSize(8);
    }
}
