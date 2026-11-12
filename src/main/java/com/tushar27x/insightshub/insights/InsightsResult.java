package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.entity.Archetype;

import java.util.Objects;

/**
 * @param stats  v1-compatible stats, stored as {@code insights.stats}
 * @param habits the signals the archetype was scored from; explains the scores
 * @param chosen the class the user plays as when it is not the scored one ({@code users.chosen_archetype}); null
 *               follows the top score. Only the AI texts read it, see {@link #playedClass()}
 */
public record InsightsResult(UserStats stats, HabitProfile habits, ArchetypeScores archetypeScores, Archetype chosen) {

    public InsightsResult(UserStats stats, HabitProfile habits, ArchetypeScores archetypeScores) {
        this(stats, habits, archetypeScores, null);
    }

    /** The scored class: stored as {@code insights.archetype}, whatever the user chose. */
    public Archetype archetype() {
        return archetypeScores.primary();
    }

    /** The class the hero plays as, {@code chosen ?? scored}: what the AI texts must name. */
    public Archetype playedClass() {
        return chosen == null ? archetype() : chosen;
    }

    /** The same result for a user who plays as {@code chosen} (null: the scored class). */
    public InsightsResult playing(Archetype chosen) {
        return Objects.equals(chosen, this.chosen) ? this : new InsightsResult(stats, habits, archetypeScores, chosen);
    }
}
