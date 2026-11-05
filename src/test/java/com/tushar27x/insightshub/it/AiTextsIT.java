package com.tushar27x.insightshub.it;

import com.tushar27x.insightshub.ai.AiField;
import com.tushar27x.insightshub.ai.AiInputs;
import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiTestData;
import com.tushar27x.insightshub.ai.CharacterSheet;
import com.tushar27x.insightshub.ai.LlmStartupCheck;
import com.tushar27x.insightshub.ai.Regeneration;
import com.tushar27x.insightshub.sync.InsightsWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B11 on a real Postgres: V7's columns and CHECK, the texts' timestamp and inputs written and read back through
 * JPA and the JSONB column, a sync that keeps the texts, and a context start that made no LLM call.
 */
@IntegrationTest
class AiTextsIT {

    static final long USER = 9_000_801L; // block 9_000_8xx, see IntegrationTest
    static final Instant T0 = Instant.parse("2026-11-05T03:00:00Z").truncatedTo(ChronoUnit.MICROS);
    static final AiInsights TEXTS = new AiInsights("year", "week", "craft", Map.of("app", "An app."),
            new CharacterSheet("it the Monk", "lore"));

    @Autowired
    InsightsWriter writer;
    @Autowired
    LlmStartupCheck startupCheck;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void freshUser() {
        jdbc.update("DELETE FROM v2.users WHERE github_id BETWEEN 9000800 AND 9000899"); // cascades to insights
        jdbc.update("INSERT INTO v2.users (github_id, login) VALUES (?, 'it-ai-texts')", USER);
    }

    @Test
    void aContextStartMakesNoLlmCall() {
        assertThat(startupCheck.callsAtReady()).isZero();
    }

    @Test
    void theTextsTimestampAndInputsRoundTrip() {
        writer.save(USER, Map.of("total_commits", 42), AiTestData.insights(), TEXTS, T0);

        assertThat(writer.storedTexts(USER))
                .isEqualTo(new Regeneration.Stored(true, T0, AiInputs.of(AiTestData.insights()), false));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT ai_inputs->>'archetype' AS archetype, ai_inputs->'top_repos'->>0 AS repo, ai_fallback "
                        + "FROM v2.insights WHERE user_id = ?", USER);
        assertThat(row).containsEntry("archetype", "MONK").containsEntry("repo", "app").containsEntry("ai_fallback", false);
    }

    @Test
    void aFallbackIsStored() {
        writer.save(USER, Map.of(), AiTestData.insights(), new AiInsights("y", "w", "c", Map.of(),
                new CharacterSheet("t", "l"), Set.of(AiField.CHARACTER_SHEET)), T0);

        assertThat(writer.storedTexts(USER).fallback()).isTrue();
    }

    @Test
    void aSyncThatKeepsTheTextsLeavesTheirColumnsAlone() {
        writer.save(USER, Map.of("total_commits", 42), AiTestData.insights(), TEXTS, T0);

        writer.saveKeepingTexts(USER, Map.of("total_commits", 50), AiTestData.insights(), T0.plusSeconds(3600));

        Map<String, Object> row = jdbc.queryForMap("SELECT year_in_review, character_title, ai_generated_at, "
                + "stats->>'total_commits' AS commits FROM v2.insights WHERE user_id = ?", USER);
        assertThat(row).containsEntry("year_in_review", "year").containsEntry("character_title", "it the Monk")
                .containsEntry("commits", "50");
        assertThat(writer.storedTexts(USER).generatedAt()).isEqualTo(T0);
    }

    @Test
    void rowsFromBeforeV7HaveNeitherAndReadAsUnknownInputs() {
        jdbc.update("INSERT INTO v2.insights (user_id, archetype, year_in_review) VALUES (?, 'MONK', 'old')", USER);

        assertThat(writer.storedTexts(USER)).isEqualTo(new Regeneration.Stored(true, null, null, false));
    }

    @Test
    void inputsNeedTheirTimestamp() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO v2.insights (user_id, ai_inputs) VALUES (?, '{}'::jsonb)",
                USER)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO v2.insights (user_id, ai_generated_at) VALUES (?, now())",
                USER)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
