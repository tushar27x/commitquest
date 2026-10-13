package com.tushar27x.insightshub.insights;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Computed stats for one user. Serializes with v1's snake_case keys
 * ({@code total_commits}, {@code last_week_heatmap}, ...) because it is stored as
 * {@code insights.stats} and returned to clients in v1's response shape.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserStats(
        int totalCommits,
        int totalPrs,
        int totalIssues,
        List<RankedRepo> rankedRepos,
        List<RepoDetail> repoDetails,
        List<String> recentCommits,
        CodeChurn codeChurn,
        List<String> topLanguages,
        int totalStars,
        int activeDaysPerYear,
        String socialRatio,
        List<Integer> lastWeekHeatmap,
        List<Integer> lastMonthHeatmap,
        List<String> heatmapLabels) {

    /** A repository ranked by the user's commit contributions to it. */
    public record RankedRepo(String name, int count) {
    }

    /** Input for the AI repo summaries (Phase 5): README text and root file names. */
    public record RepoDetail(String name, String readme, List<String> files) {
    }

    /** Lines added/removed across the user's recent pull requests. */
    public record CodeChurn(int additions, int deletions) {
    }
}
