package com.tushar27x.insightshub.ai;

import com.tushar27x.insightshub.ai.Regeneration.Reason;
import com.tushar27x.insightshub.ai.Regeneration.Stored;
import com.tushar27x.insightshub.entity.Archetype;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** B11: the daily job rewrites a user's texts only when the data moved since they were written. */
class RegenerationTest {

    private static final AiProperties.Regenerate RULES = AiProperties.Regenerate.DEFAULTS; // 5 points, 10%, 7 days
    /** The job's run, 03:00 UTC. */
    private static final Instant NOW = Instant.parse("2026-11-05T03:00:02Z");
    /** Written by yesterday's run. */
    private static final Instant YESTERDAY = Instant.parse("2026-11-04T03:00:05Z");

    private static final AiInputs BEFORE = new AiInputs(Archetype.MONK,
            Map.of("MONK", 52, "ROGUE", 47, "PALADIN", 44), 100, 20, 30, List.of("api", "cli", "web"),
            List.of("Java", "Go", "TypeScript"));

    private static Stored written(Instant at, AiInputs inputs) {
        return new Stored(true, at, inputs, false);
    }

    private static Optional<Reason> decide(AiInputs now) {
        return Regeneration.decide(written(YESTERDAY, BEFORE), now, NOW, RULES);
    }

    private static AiInputs with(Map<String, Integer> scores) {
        Map<String, Integer> merged = new HashMap<>(BEFORE.scores());
        merged.putAll(scores);
        return new AiInputs(BEFORE.archetype(), merged, BEFORE.totalCommits(), BEFORE.totalPrs(), BEFORE.totalStars(),
                BEFORE.topRepos(), BEFORE.topLanguages());
    }

    private static AiInputs withTotals(int commits, int prs, int stars) {
        return new AiInputs(BEFORE.archetype(), BEFORE.scores(), commits, prs, stars, BEFORE.topRepos(),
                BEFORE.topLanguages());
    }

    @Test
    void belowEveryThresholdKeepsTheTexts() {
        AiInputs drift = new AiInputs(Archetype.MONK, Map.of("MONK", 56, "ROGUE", 43, "PALADIN", 44),
                109, 21, 32, List.of("web", "api", "cli"), List.of("Go", "Java", "TypeScript")); // reordered only

        assertThat(decide(drift)).isEmpty();
        assertThat(decide(BEFORE)).isEmpty();
    }

    @Test
    void theScoredClassChanged() {
        AiInputs rogue = new AiInputs(Archetype.ROGUE, BEFORE.scores(), 100, 20, 30, BEFORE.topRepos(),
                BEFORE.topLanguages());

        assertThat(decide(rogue)).contains(Reason.CLASS_CHANGED);
    }

    @Test
    void anyClassScoreMovedFivePoints() {
        assertThat(decide(with(Map.of("PALADIN", 49)))).contains(Reason.SCORE_MOVED);
        assertThat(decide(with(Map.of("PALADIN", 39)))).contains(Reason.SCORE_MOVED);
        assertThat(decide(with(Map.of("PALADIN", 48)))).isEmpty();
        assertThat(decide(with(Map.of("BARD", 5)))).as("a class new to the map counts from 0").contains(Reason.SCORE_MOVED);
    }

    @Test
    void commitsPrsOrStarsChangedTenPercent() {
        assertThat(decide(withTotals(110, 20, 30))).contains(Reason.STATS_CHANGED);
        assertThat(decide(withTotals(90, 20, 30))).contains(Reason.STATS_CHANGED);
        assertThat(decide(withTotals(109, 20, 30))).isEmpty();
        assertThat(decide(withTotals(100, 22, 30))).contains(Reason.STATS_CHANGED);
        assertThat(decide(withTotals(100, 20, 33))).contains(Reason.STATS_CHANGED);
        assertThat(decide(withTotals(100, 20, 32))).isEmpty();
    }

    @Test
    void fromZeroAnyChangeCounts() {
        AiInputs noStars = withTotals(100, 20, 0);

        assertThat(Regeneration.decide(written(YESTERDAY, noStars), withTotals(100, 20, 1), NOW, RULES))
                .contains(Reason.STATS_CHANGED);
        assertThat(Regeneration.decide(written(YESTERDAY, noStars), noStars, NOW, RULES)).isEmpty();
    }

    @Test
    void theTopReposOrLanguagesChanged() {
        AiInputs newRepo = new AiInputs(Archetype.MONK, BEFORE.scores(), 100, 20, 30, List.of("api", "cli", "game"),
                BEFORE.topLanguages());
        AiInputs newLanguage = new AiInputs(Archetype.MONK, BEFORE.scores(), 100, 20, 30, BEFORE.topRepos(),
                List.of("Java", "Go", "Rust"));

        assertThat(decide(newRepo)).contains(Reason.TOP_REPOS_CHANGED);
        assertThat(decide(newLanguage)).contains(Reason.TOP_LANGUAGES_CHANGED);
    }

    @Test
    void sevenDayOldTextsAreRewrittenThoughNothingChanged() {
        // written a week ago a few seconds after today's run time: still due today, in whole UTC days
        Instant weekAgo = Instant.parse("2026-10-29T03:00:09Z");
        Instant sixDaysAgo = Instant.parse("2026-10-30T02:00:00Z");

        assertThat(Regeneration.decide(written(weekAgo, BEFORE), BEFORE, NOW, RULES)).contains(Reason.STALE);
        assertThat(Regeneration.decide(written(sixDaysAgo, BEFORE), BEFORE, NOW, RULES)).isEmpty();
    }

    @Test
    void textsFromAFallbackAreRewritten() {
        Stored fallback = new Stored(true, YESTERDAY, BEFORE, true);

        assertThat(Regeneration.decide(fallback, BEFORE, NOW, RULES)).contains(Reason.FALLBACK);
    }

    @Test
    void textsFromBeforeV7AreRewrittenOnce() {
        assertThat(Regeneration.decide(new Stored(true, null, null, false), BEFORE, NOW, RULES))
                .contains(Reason.NO_INPUTS);
    }

    @Test
    void noTextsAtAll() {
        assertThat(Regeneration.decide(Stored.NONE, BEFORE, NOW, RULES)).contains(Reason.NO_TEXTS);
    }

    @Test
    void atMostOncePerUtcDay() {
        // written at the first login earlier today, or by an earlier run today: kept, whatever moved or fell back
        Instant earlierToday = Instant.parse("2026-11-05T00:10:00Z");
        AiInputs rogue = new AiInputs(Archetype.ROGUE, Map.of(), 0, 0, 0, List.of(), List.of());

        assertThat(Regeneration.decide(new Stored(true, earlierToday, BEFORE, true), rogue, NOW, RULES)).isEmpty();
        assertThat(Regeneration.decide(written(NOW, BEFORE), rogue, NOW.plusSeconds(60), RULES)).isEmpty();
    }

    @Test
    void thresholdsComeFromTheProperties() {
        AiProperties.Regenerate strict = new AiProperties.Regenerate(1, 0.01, Duration.ofDays(1), 1);

        assertThat(Regeneration.decide(written(YESTERDAY, BEFORE), BEFORE, NOW, strict)).contains(Reason.STALE);
        assertThat(Regeneration.decide(written(NOW.minusSeconds(60), BEFORE), BEFORE, NOW, strict)).isEmpty();
        AiProperties.Regenerate loose = new AiProperties.Regenerate(20, 0.5, Duration.ofDays(30), 1);
        assertThat(Regeneration.decide(written(YESTERDAY, BEFORE), with(Map.of("PALADIN", 60)), NOW, loose)).isEmpty();
    }
}
