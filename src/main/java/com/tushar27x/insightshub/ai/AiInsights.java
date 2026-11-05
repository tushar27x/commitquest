package com.tushar27x.insightshub.ai;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything the AI layer writes for one user. Every field is always filled: with the LLM's answer,
 * or with that field's static fallback when the answer lacked it, was malformed, or the call failed.
 *
 * @param yearInReview  the mentor's overall take on the year (replaces v1's roast)
 * @param repoSummaries one-sentence summary per repository name (v1: the first 3 repo details)
 * @param fallbacks     the fields that hold a static fallback instead of the LLM's text; the daily job
 *                      regenerates texts that have any (B11)
 */
public record AiInsights(
        String yearInReview,
        String weeklyReview,
        String craftReview,
        Map<String, String> repoSummaries,
        CharacterSheet characterSheet,
        Set<AiField> fallbacks) {

    public AiInsights {
        fallbacks = fallbacks.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(fallbacks));
    }

    /** All five from the LLM. */
    public AiInsights(String yearInReview, String weeklyReview, String craftReview,
                      Map<String, String> repoSummaries, CharacterSheet characterSheet) {
        this(yearInReview, weeklyReview, craftReview, repoSummaries, characterSheet, Set.of());
    }

    /** At least one field is a static fallback. */
    public boolean fallback() {
        return !fallbacks.isEmpty();
    }
}
