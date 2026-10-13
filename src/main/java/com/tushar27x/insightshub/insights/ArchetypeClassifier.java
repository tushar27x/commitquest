package com.tushar27x.insightshub.insights;

/**
 * Scores a user's habits against every archetype. Behind an interface so the formula can be
 * replaced (or run side by side with a new version) without touching the engine.
 */
public interface ArchetypeClassifier {

    ArchetypeScores classify(HabitProfile habits);
}
