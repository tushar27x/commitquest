package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.github.model.GitHubUser;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden test against v1. The expected file was produced by running v1's Python
 * {@code calculate_user_insights} on {@code v1-parity-user.json}:
 * <pre>
 * python3: m.calculate_user_insights(json.load(open("v1-parity-user.json")))
 * </pre>
 * The input has no empty repository, because v1 crashes on those (BUGS.md B20).
 * <p>
 * Only {@code stats} is compared: archetypes intentionally differ from v1 (scored D&amp;D classes
 * instead of v1's five first-match rules, from the PR #3 review).
 */
class InsightsEngineParityTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final InsightsEngine engine = new InsightsEngine(new ScoringArchetypeClassifier());

    @Test
    void matchesV1OutputForTheSameInput() throws IOException {
        GitHubUser user = read("insights/v1-parity-user.json", GitHubUser.class);
        JsonNode expected = read("insights/v1-expected-insights.json", JsonNode.class);

        InsightsResult result = engine.calculate(user);
        JsonNode actualStats = json.valueToTree(result.stats());

        assertThat(actualStats)
                .as("stats JSON (keys and values) must equal v1's")
                .isEqualTo(expected.get("stats"));
    }

    private <T> T read(String path, Class<T> type) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readValue(in, type);
        }
    }
}
