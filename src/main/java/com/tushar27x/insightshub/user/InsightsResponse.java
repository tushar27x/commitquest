package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.ai.CharacterSheet;
import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.entity.Insights;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.Map;

/**
 * {@code GET /api/user/insights}, and the value cached in Redis. A DTO, never the entity:
 * no lazy-loading surprises and no way to leak the encrypted token.
 * <p>
 * v1's keys where they still apply ({@code user_name}, {@code stats}, {@code weekly_review},
 * {@code craft_review}, {@code updated_at}); v1's {@code display_json} roast is now {@code year_in_review}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InsightsResponse(
        String userName,
        Archetype archetype,
        Map<String, Integer> archetypeScores,
        Map<String, Object> stats,
        String yearInReview,
        String weeklyReview,
        String craftReview,
        Map<String, String> repoSummaries,
        CharacterSheet characterSheet,
        Instant updatedAt) {

    static InsightsResponse from(Insights insights) {
        CharacterSheet sheet = insights.getCharacterTitle() == null ? null
                : new CharacterSheet(insights.getCharacterTitle(), insights.getCharacterLore());
        return new InsightsResponse(
                insights.getUser().getLogin(),
                insights.getArchetype(),
                Map.copyOf(insights.getArchetypeScores()),
                insights.getStats(),
                insights.getYearInReview(),
                insights.getWeeklyReview(),
                insights.getCraftReview(),
                Map.copyOf(insights.getRepoSummaries()),
                sheet,
                insights.getUpdatedAt());
    }
}
