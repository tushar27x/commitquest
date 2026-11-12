package com.tushar27x.insightshub.ai;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.insights.ArchetypeScores;
import com.tushar27x.insightshub.insights.HabitProfile;
import com.tushar27x.insightshub.insights.InsightsResult;
import com.tushar27x.insightshub.insights.UserStats;
import com.tushar27x.insightshub.insights.UserStats.RepoDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The AI texts, written in a supportive mentor persona: a year in review (replaces v1's roast), v1's weekly
 * review, craft review and repo summaries ({@code services/ai_service.py}), plus the D&amp;D character sheet.
 * <p>
 * B11: all five come from <b>one</b> call on the primary tier (until then, five calls per sync). The model answers
 * one JSON object, described by Spring AI's {@link BeanOutputConverter} schema of {@link Answer}; each field is read
 * and checked on its own, so a missing, empty or malformed field falls back alone, and a call that fails on every
 * provider falls back for all five. One user per prompt, always: one user's data never reaches another's text.
 * <p>
 * Lists are formatted here, not in the template: StringTemplate renders a list by concatenating its elements
 * with no separator ({@code [1, 2, 3]} becomes {@code 123}).
 */
@Component
@RequiredArgsConstructor
public class AiReviewer {

    static final String YEAR_IN_REVIEW_PENDING = "Your year in review is on its way.";
    static final String WEEKLY_PENDING = "Activity analysis in progress.";
    static final String CRAFT_PENDING = "Code quality audit scheduled.";
    static final String DEFAULT_REPO_SUMMARY = "Exploration of technical concepts and implementations.";

    static final int SUMMARIZED_REPOS = 3;
    static final int README_CHARS = 1000;
    static final int CRAFT_COMMITS = 10;
    static final int TITLE_MAX_CHARS = 150;
    /** A text longer than this is not the short note that was asked for: it falls back. */
    static final int TEXT_MAX_CHARS = 2000;
    /** The five fields together, as the five calls had them (300 + 200 + 250 + 3 × 120 + 300) plus some slack. */
    static final int MAX_TOKENS = 1500;

    private static final Resource PROMPT = new ClassPathResource("prompts/insights-texts.st");
    private static final String FORMAT = new BeanOutputConverter<>(Answer.class).getFormat();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final LlmChain llm;

    /** The one combined call. Never throws for a bad answer: every field missing from it gets its fallback. */
    public AiInsights review(String login, InsightsResult insights) {
        UserStats stats = insights.stats();
        Archetype archetype = insights.playedClass();
        String className = className(archetype);
        List<RepoDetail> repos = stats.repoDetails().stream().limit(SUMMARIZED_REPOS).toList();
        List<Integer> heatmap = stats.lastWeekHeatmap();

        Parsed parsed = llm.json("insights texts", ModelTier.STANDARD, MAX_TOKENS, spec -> spec.user(u -> u.text(PROMPT)
                                .param("login", login)
                                .param("className", className)
                                .param("habit", archetype.getHabit())
                                .param("commits", stats.totalCommits())
                                .param("activeDays", stats.activeDaysPerYear())
                                .param("stars", stats.totalStars())
                                .param("prs", stats.totalPrs())
                                .param("languages", listOrNone(stats.topLanguages()))
                                .param("weekTotal", heatmap.stream().mapToInt(Integer::intValue).sum())
                                .param("weekActiveDays", heatmap.stream().filter(count -> count > 0).count())
                                .param("distribution", distribution(heatmap, stats.heatmapLabels()))
                                .param("recentCommits", recentCommits(stats))
                                .param("additions", stats.codeChurn().additions())
                                .param("deletions", stats.codeChurn().deletions())
                                .param("scores", topScores(insights.archetypeScores()))
                                .param("evidence", evidence(insights.habits()))
                                .param("repos", repos.isEmpty() ? "(none)"
                                        : repos.stream().map(AiReviewer::describeRepo).collect(Collectors.joining("\n\n")))
                                .param("outputFormat", FORMAT)),
                        AiReviewer::parse, Parsed::anyUsable)
                .orElse(Parsed.NOTHING);
        return assemble(login, archetype, repos, parsed);
    }

    /** Every field's fallback: the call failed or timed out. */
    public static AiInsights fallbacks(String login, InsightsResult insights) {
        List<RepoDetail> repos = insights.stats().repoDetails().stream().limit(SUMMARIZED_REPOS).toList();
        return assemble(login, insights.playedClass(), repos, Parsed.NOTHING);
    }

    static AiInsights assemble(String login, Archetype archetype, List<RepoDetail> repos, Parsed parsed) {
        Set<AiField> fallbacks = EnumSet.noneOf(AiField.class);
        String year = textOr(parsed.yearInReview(), YEAR_IN_REVIEW_PENDING, AiField.YEAR_IN_REVIEW, fallbacks);
        String weekly = textOr(parsed.weeklyReview(), WEEKLY_PENDING, AiField.WEEKLY_REVIEW, fallbacks);
        String craft = textOr(parsed.craftReview(), CRAFT_PENDING, AiField.CRAFT_REVIEW, fallbacks);

        Map<String, String> answered = matching(parsed.repoSummaries(), repos);
        Map<String, String> summaries = new LinkedHashMap<>();
        repos.forEach(repo -> summaries.put(repo.name(), answered.getOrDefault(repo.name(), DEFAULT_REPO_SUMMARY)));
        if (answered.size() < repos.size()) {
            fallbacks.add(AiField.REPO_SUMMARIES);
        }

        CharacterSheet sheet = parsed.characterSheet();
        if (sheet != null && isValidSheet(sheet, className(archetype))) {
            sheet = new CharacterSheet(sheet.title().strip(), sheet.lore().strip());
        } else {
            sheet = fallbackSheet(login, archetype);
            fallbacks.add(AiField.CHARACTER_SHEET);
        }
        return new AiInsights(year, weekly, craft, summaries, sheet, fallbacks);
    }

    private static String textOr(String text, String fallback, AiField field, Set<AiField> fallbacks) {
        if (text == null || text.isBlank() || text.length() > TEXT_MAX_CHARS) {
            fallbacks.add(field);
            return fallback;
        }
        return text.strip();
    }

    /**
     * Reads the answer field by field: a field of the wrong JSON type is treated as missing, the others still
     * count. Tolerates a markdown fence or chatter around the object. Null when there's no JSON object at all.
     */
    static Parsed parse(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        JsonNode root;
        try {
            root = JSON.readTree(raw.substring(start, end + 1));
        } catch (JacksonException e) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        return new Parsed(string(root.get("yearInReview")), string(root.get("weeklyReview")),
                string(root.get("craftReview")), summaries(root.get("repoSummaries")), sheet(root.get("characterSheet")));
    }

    private static String string(JsonNode node) {
        return node != null && node.isString() ? node.stringValue() : null;
    }

    private static List<RepoSummary> summaries(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<RepoSummary> summaries = new ArrayList<>();
        for (JsonNode entry : node.values()) {
            if (entry != null && entry.isObject()) {
                summaries.add(new RepoSummary(string(entry.get("name")), string(entry.get("summary"))));
            }
        }
        return summaries;
    }

    private static CharacterSheet sheet(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return new CharacterSheet(string(node.get("title")), string(node.get("lore")));
    }

    static CharacterSheet fallbackSheet(String login, Archetype archetype) {
        String className = className(archetype);
        return new CharacterSheet(login + " the " + className,
                "A " + className + " who " + archetype.getHabit() + ". The bards are still writing the rest of this tale.");
    }

    /** The class is decided by code: a title that doesn't name it is rejected. */
    static boolean isValidSheet(CharacterSheet sheet, String className) {
        return sheet.title() != null && !sheet.title().isBlank() && sheet.title().length() <= TITLE_MAX_CHARS
                && sheet.lore() != null && !sheet.lore().isBlank() && sheet.lore().length() <= TEXT_MAX_CHARS
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

    /** The first {@value #CRAFT_COMMITS} commit subjects, quoted. */
    static String recentCommits(UserStats stats) {
        String commits = stats.recentCommits().stream()
                .limit(CRAFT_COMMITS)
                .map(message -> "\"" + message.lines().findFirst().orElse("").strip() + "\"")
                .collect(Collectors.joining(", "));
        return commits.isEmpty() ? "(none)" : commits;
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

    private static String listOrNone(List<String> values) {
        return values.isEmpty() ? "(none)" : String.join(", ", values);
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

    /** Answers for repos that were asked about, keyed by the requested name; invented names are dropped. */
    private static Map<String, String> matching(List<RepoSummary> answers, List<RepoDetail> repos) {
        Map<String, String> byName = new LinkedHashMap<>();
        for (RepoSummary summary : answers) {
            if (summary.name() == null || summary.summary() == null || summary.summary().isBlank()
                    || summary.summary().length() > TEXT_MAX_CHARS) {
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

    /** The JSON shape asked of the model: only its schema is used; the answer is read by {@link #parse}. */
    record Answer(String yearInReview, String weeklyReview, String craftReview, List<RepoSummary> repoSummaries,
                  CharacterSheet characterSheet) {
    }

    record RepoSummary(String name, String summary) {
    }

    /** What {@link #parse} could read; a null field was missing or of the wrong type. */
    record Parsed(String yearInReview, String weeklyReview, String craftReview, List<RepoSummary> repoSummaries,
                  CharacterSheet characterSheet) {

        static final Parsed NOTHING = new Parsed(null, null, null, List.of(), null);

        /** Worth keeping instead of asking the next provider: at least one field came through. */
        boolean anyUsable() {
            return usable(yearInReview) || usable(weeklyReview) || usable(craftReview)
                    || repoSummaries.stream().anyMatch(s -> usable(s.summary()))
                    || (characterSheet != null && usable(characterSheet.title()));
        }

        private static boolean usable(String text) {
            return text != null && !text.isBlank();
        }
    }
}
