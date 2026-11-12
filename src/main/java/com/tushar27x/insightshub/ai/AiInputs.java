package com.tushar27x.insightshub.ai;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.insights.InsightsResult;
import com.tushar27x.insightshub.insights.UserStats;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The inputs the AI texts were written from, as far as {@link Regeneration} compares them: stored with the texts
 * ({@code insights.ai_inputs}, V7), so the daily job compares today's data with what the texts describe, not with
 * yesterday's sync.
 *
 * @param scores   every class's score, keyed by {@link Archetype} name
 * @param topRepos the repos the summaries describe (the first {@value AiReviewer#SUMMARIZED_REPOS} repo details):
 *                 when they change, the stored summaries no longer match
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiInputs(
        Archetype archetype, // the class played, chosen ?? scored: the texts name it
        Map<String, Integer> scores,
        int totalCommits,
        int totalPrs,
        int totalStars,
        List<String> topRepos,
        List<String> topLanguages) {

    public AiInputs {
        scores = scores == null ? Map.of() : Map.copyOf(scores);
        topRepos = topRepos == null ? List.of() : List.copyOf(topRepos);
        topLanguages = topLanguages == null ? List.of() : List.copyOf(topLanguages);
    }

    public static AiInputs of(InsightsResult result) {
        UserStats stats = result.stats();
        Map<String, Integer> scores = new LinkedHashMap<>();
        result.archetypeScores().scores().forEach((archetype, score) -> scores.put(archetype.name(), score));
        return new AiInputs(result.playedClass(), scores, stats.totalCommits(), stats.totalPrs(), stats.totalStars(),
                stats.repoDetails().stream().limit(AiReviewer.SUMMARIZED_REPOS).map(UserStats.RepoDetail::name).toList(),
                stats.topLanguages());
    }
}
