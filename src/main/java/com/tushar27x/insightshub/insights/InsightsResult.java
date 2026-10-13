package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.entity.Archetype;

/**
 * @param stats  v1-compatible stats, stored as {@code insights.stats}
 * @param habits the signals the archetype was scored from; explains the scores
 */
public record InsightsResult(UserStats stats, HabitProfile habits, ArchetypeScores archetypeScores) {

    public Archetype archetype() {
        return archetypeScores.primary();
    }
}
