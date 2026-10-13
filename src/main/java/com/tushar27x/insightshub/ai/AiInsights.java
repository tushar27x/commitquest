package com.tushar27x.insightshub.ai;

import java.util.Map;

/**
 * Everything the AI layer writes for one user. Every field is always filled: with the LLM's answer,
 * or with a static fallback when all providers failed.
 *
 * @param repoSummaries one-sentence summary per repository name (v1: the first 3 repo details)
 */
public record AiInsights(
        String roast,
        String weeklyReview,
        String craftReview,
        Map<String, String> repoSummaries,
        CharacterSheet characterSheet) {
}
