package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.github.model.GitHubUser;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden test against v1. The expected file was produced by running v1's Python
 * {@code calculate_user_insights} on {@code v1-parity-user.json}:
 * <pre>
 * python3: m.calculate_user_insights(json.load(open("v1-parity-user.json")))
 * </pre>
 * The input has no empty repository, because v1 crashes on those (BUGS.md B20).
 */
class InsightsEngineParityTest {

    /** v1 returned display strings; v2 stores the enum. */
    private static final Map<String, Archetype> V1_LABELS = Map.of(
            "🕵️ The Bug Hunter", Archetype.BUG_HUNTER,
            "🌟 The Open Source Hero", Archetype.OPEN_SOURCE_HERO,
            "🏗️ The Software Architect", Archetype.SOFTWARE_ARCHITECT,
            "🛡️ The Weekend Warrior", Archetype.WEEKEND_WARRIOR,
            "💻 The Code Crusader", Archetype.CODE_CRUSADER);

    private final JsonMapper json = JsonMapper.builder().build();
    private final InsightsEngine engine = new InsightsEngine(new RuleBasedArchetypeClassifier());

    @Test
    void matchesV1OutputForTheSameInput() throws IOException {
        GitHubUser user = read("insights/v1-parity-user.json", GitHubUser.class);
        JsonNode expected = read("insights/v1-expected-insights.json", JsonNode.class);

        InsightsResult result = engine.calculate(user);
        JsonNode actualStats = json.valueToTree(result.stats());

        assertThat(actualStats)
                .as("stats JSON (keys and values) must equal v1's")
                .isEqualTo(expected.get("stats"));
        assertThat(result.archetype())
                .isEqualTo(V1_LABELS.get(expected.get("archetype").asString()));
    }

    private <T> T read(String path, Class<T> type) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readValue(in, type);
        }
    }
}
