package com.tushar27x.insightshub.ai;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.insights.ArchetypeScores;
import com.tushar27x.insightshub.insights.HabitProfile;
import com.tushar27x.insightshub.insights.UserStats;
import com.tushar27x.insightshub.insights.UserStats.RepoDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The AI generators: v1's roast, weekly review, craft review and repo summaries
 * ({@code services/ai_service.py}), plus the D&amp;D character sheet. Each one always returns something:
 * the LLM's answer, or a static fallback text.
 * <p>
 * Lists are formatted here, not in the templates: StringTemplate renders a list by concatenating its
 * elements with no separator ({@code [1, 2, 3]} becomes {@code 123}).
 */
@Component
@RequiredArgsConstructor
public class AiReviewer {

    /** v1's text when both providers failed. */
    static final String PROVIDERS_DOWN = "Your code is so average it didn't even trigger my fallback circuits. Try harder.";
    static final String DEFAULT_REPO_SUMMARY = "Exploration of technical concepts and implementations.";

    static final int SUMMARIZED_REPOS = 3;
    static final int README_CHARS = 1000;
    static final int CRAFT_COMMITS = 10;
    static final int TITLE_MAX_CHARS = 150;

    private static final Resource ROAST = new ClassPathResource("prompts/roast.st");
    private static final Resource WEEKLY_REVIEW = new ClassPathResource("prompts/weekly-review.st");
    private static final Resource CRAFT_REVIEW = new ClassPathResource("prompts/craft-review.st");
    private static final Resource REPO_SUMMARIES = new ClassPathResource("prompts/repo-summaries.st");
    private static final Resource REPO_SUMMARIES_SYSTEM = new ClassPathResource("prompts/repo-summaries-system.st");
    private static final Resource CHARACTER_SHEET = new ClassPathResource("prompts/character-sheet.st");

    private final LlmChain llm;

    public String roast(UserStats stats, Archetype archetype) {
        return llm.text("roast", ModelTier.STANDARD, 300, spec -> spec.user(u -> u.text(ROAST)
                        .param("archetype", archetype.getLabel() + " (" + archetype.getHabit() + ")")
                        .param("commits", stats.totalCommits())
                        .param("activeDays", stats.activeDaysPerYear())
                        .param("stars", stats.totalStars())
                        .param("prs", stats.totalPrs())
                        .param("languages", String.join(", ", stats.topLanguages()))))
                .orElse(PROVIDERS_DOWN);
    }

    public String weeklyReview(UserStats stats) {
        List<Integer> heatmap = stats.lastWeekHeatmap();
        return llm.text("weekly review", ModelTier.STANDARD, 200, spec -> spec.user(u -> u.text(WEEKLY_REVIEW)
                        .param("total", heatmap.stream().mapToInt(Integer::intValue).sum())
                        .param("activeDays", heatmap.stream().filter(count -> count > 0).count())
                        .param("distribution", distribution(heatmap, stats.heatmapLabels()))))
                .orElse(PROVIDERS_DOWN);
    }

    public String craftReview(UserStats stats) {
        String commits = stats.recentCommits().stream()
                .limit(CRAFT_COMMITS)
                .map(message -> "\"" + message.lines().findFirst().orElse("").strip() + "\"")
                .collect(Collectors.joining(", "));
        return llm.text("craft review", ModelTier.STANDARD, 250, spec -> spec.user(u -> u.text(CRAFT_REVIEW)
                        .param("commits", commits.isEmpty() ? "(none)" : commits)
                        .param("additions", stats.codeChurn().additions())
                        .param("deletions", stats.codeChurn().deletions())))
                .orElse(PROVIDERS_DOWN);
    }

    /**
     * One structured call for the first {@value #SUMMARIZED_REPOS} repos (v1 made one call per repo).
     * Names the LLM made up are dropped; repos it skipped get the default summary.
     */
    public Map<String, String> repoSummaries(List<RepoDetail> repoDetails) {
        List<RepoDetail> repos = repoDetails.stream().limit(SUMMARIZED_REPOS).toList();
        if (repos.isEmpty()) {
            return Map.of();
        }
        String block = repos.stream().map(AiReviewer::describeRepo).collect(Collectors.joining("\n\n"));

        Map<String, String> answered = llm.call("repo summaries", ModelTier.FAST, 120 * repos.size(),
                        spec -> spec.system(REPO_SUMMARIES_SYSTEM)
                                .user(u -> u.text(REPO_SUMMARIES).param("repos", block))
                                .call().entity(RepoSummaries.class),
                        result -> result.summaries() != null && !matching(result, repos).isEmpty())
                .map(result -> matching(result, repos))
                .orElse(Map.of());

        Map<String, String> summaries = new LinkedHashMap<>();
        repos.forEach(repo -> summaries.put(repo.name(), answered.getOrDefault(repo.name(), DEFAULT_REPO_SUMMARY)));
        return summaries;
    }

    public CharacterSheet characterSheet(String login, ArchetypeScores scores, HabitProfile habits,
                                         List<String> languages) {
        Archetype archetype = scores.primary();
        String className = className(archetype);
        Optional<CharacterSheet> sheet = llm.call("character sheet", ModelTier.STANDARD, 300,
                spec -> spec.user(u -> u.text(CHARACTER_SHEET)
                                .param("login", login)
                                .param("className", className)
                                .param("habit", archetype.getHabit())
                                .param("scores", topScores(scores))
                                .param("evidence", evidence(habits))
                                .param("languages", languages.isEmpty() ? "(none)" : String.join(", ", languages)))
                        .call().entity(CharacterSheet.class),
                answer -> isValidSheet(answer, className));
        return sheet
                .map(answer -> new CharacterSheet(answer.title().strip(), answer.lore().strip()))
                .orElseGet(() -> fallbackSheet(login, archetype));
    }

    static CharacterSheet fallbackSheet(String login, Archetype archetype) {
        String className = className(archetype);
        return new CharacterSheet(login + " the " + className,
                "A " + className + " who " + archetype.getHabit() + ". The bards are still writing the rest of this tale.");
    }

    /** The class is decided by code: a title that doesn't name it is rejected. */
    static boolean isValidSheet(CharacterSheet sheet, String className) {
        return sheet.title() != null && !sheet.title().isBlank() && sheet.title().length() <= TITLE_MAX_CHARS
                && sheet.lore() != null && !sheet.lore().isBlank()
                && sheet.title().toLowerCase(Locale.ROOT).contains(className.toLowerCase(Locale.ROOT));
    }

    /** {@code PALADIN} → {@code Paladin}. */
    static String className(Archetype archetype) {
        String name = archetype.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    /** "Mon 0, Tue 3, ..." (v1 sent only the numbers, though its prompt asks about Sunday). */
    static String distribution(List<Integer> heatmap, List<String> labels) {
        if (labels.size() != heatmap.size()) {
            return heatmap.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return IntStream.range(0, heatmap.size())
                .mapToObj(i -> labels.get(i) + " " + heatmap.get(i))
                .collect(Collectors.joining(", "));
    }

    /** The three strongest classes, e.g. "Monk 52, Rogue 47, Paladin 44". */
    static String topScores(ArchetypeScores scores) {
        return scores.scores().entrySet().stream()
                .sorted(Map.Entry.<Archetype, Integer>comparingByValue().reversed()) // stable: ties keep class order
                .limit(3)
                .map(entry -> className(entry.getKey()) + " " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    static String evidence(HabitProfile h) {
        return String.join("; ",
                "active on %d%% of days, longest streak %d days".formatted(percent(h.activeDayRatio()), h.longestStreak()),
                "%d%% of contributions on weekends".formatted(percent(h.weekendShare())),
                "%d languages".formatted(h.languageCount()),
                h.prCount() == 0 ? "no recent pull requests"
                        : "average pull request %d lines changed".formatted(Math.round(h.avgPrSize())),
                h.ownCommitCount() == 0 ? "no recent commits of their own"
                        : "%d%% descriptive commit messages, %d%% mention fixes".formatted(
                        percent(h.descriptiveCommitShare()), percent(h.fixCommitShare())),
                "%d%% of top repos have infrastructure files".formatted(percent(h.infraRepoShare())),
                "%d followers + stars".formatted(h.audience()));
    }

    private static String describeRepo(RepoDetail repo) {
        String readme = repo.readme() == null || repo.readme().isBlank() ? "(none)"
                : repo.readme().substring(0, Math.min(README_CHARS, repo.readme().length()));
        return """
                REPOSITORY: %s
                FILES: %s
                README (first %d characters):
                <<<
                %s
                >>>""".formatted(repo.name(), String.join(", ", repo.files()), README_CHARS, readme);
    }

    /** Answers for repos that were asked about, keyed by the requested name. */
    private static Map<String, String> matching(RepoSummaries result, List<RepoDetail> repos) {
        Map<String, String> byName = new LinkedHashMap<>();
        for (RepoSummary summary : result.summaries()) {
            if (summary == null || summary.name() == null || summary.summary() == null || summary.summary().isBlank()) {
                continue;
            }
            repos.stream()
                    .map(RepoDetail::name)
                    .filter(name -> name.equalsIgnoreCase(summary.name().strip()))
                    .findFirst()
                    .ifPresent(name -> byName.putIfAbsent(name, summary.summary().strip()));
        }
        return byName;
    }

    private static long percent(double share) {
        return Math.round(share * 100);
    }

    /** Structured output shape for {@link #repoSummaries}. */
    record RepoSummaries(List<RepoSummary> summaries) {
    }

    record RepoSummary(String name, String summary) {
    }
}
