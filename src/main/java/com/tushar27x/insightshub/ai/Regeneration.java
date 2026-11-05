package com.tushar27x.insightshub.ai;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * B11: whether the daily job rewrites a user's AI texts. Pure: no clock, database or Spring.
 * <p>
 * The texts are compared with the inputs they were written from ({@link AiInputs}, stored with them), so slow drift
 * adds up until it crosses a threshold instead of hiding under each day's small step. Days are UTC days, the job's
 * own schedule.
 */
public final class Regeneration {

    private Regeneration() {
    }

    /** Why the texts are rewritten, checked in this order; the first that applies is logged. */
    public enum Reason {
        /** No texts yet (a first sync that failed before writing). */
        NO_TEXTS,
        /** Texts from before V7: nobody knows what they were written from. */
        NO_INPUTS,
        /** At least one field holds a static fallback. */
        FALLBACK,
        /** {@code max-age} days or more since they were written: the weekly review describes "this week". */
        STALE,
        CLASS_CHANGED,
        /** A class score moved by {@code score-delta} points or more. */
        SCORE_MOVED,
        /** Total commits, PRs or stars changed by {@code stat-change} (a share) or more. */
        STATS_CHANGED,
        TOP_REPOS_CHANGED,
        TOP_LANGUAGES_CHANGED
    }

    /**
     * What is stored for the user now.
     *
     * @param hasTexts    the texts exist (the year in review is the marker: every write fills it)
     * @param generatedAt when they were written; null before V7
     * @param inputs      what they were written from; null before V7
     * @param fallback    at least one field is a static fallback
     */
    public record Stored(boolean hasTexts, Instant generatedAt, AiInputs inputs, boolean fallback) {

        public static final Stored NONE = new Stored(false, null, null, false);
    }

    /**
     * @return the reason to rewrite, or empty to keep the texts. Never twice on one UTC day: texts written today
     * (by the first login or an earlier run) stay, whatever changed since.
     */
    public static Optional<Reason> decide(Stored stored, AiInputs current, Instant now, AiProperties.Regenerate rules) {
        LocalDate today = utcDay(now);
        if (stored.generatedAt() != null && !utcDay(stored.generatedAt()).isBefore(today)) {
            return Optional.empty();
        }
        if (!stored.hasTexts()) {
            return Optional.of(Reason.NO_TEXTS);
        }
        AiInputs before = stored.inputs();
        if (before == null || stored.generatedAt() == null) {
            return Optional.of(Reason.NO_INPUTS);
        }
        if (stored.fallback()) {
            return Optional.of(Reason.FALLBACK);
        }
        if (ChronoUnit.DAYS.between(utcDay(stored.generatedAt()), today) >= rules.maxAge().toDays()) {
            return Optional.of(Reason.STALE);
        }
        if (before.archetype() != current.archetype()) {
            return Optional.of(Reason.CLASS_CHANGED);
        }
        if (scoreMoved(before.scores(), current.scores(), rules.scoreDelta())) {
            return Optional.of(Reason.SCORE_MOVED);
        }
        if (changed(before.totalCommits(), current.totalCommits(), rules.statChange())
                || changed(before.totalPrs(), current.totalPrs(), rules.statChange())
                || changed(before.totalStars(), current.totalStars(), rules.statChange())) {
            return Optional.of(Reason.STATS_CHANGED);
        }
        if (!sameMembers(before.topRepos(), current.topRepos())) {
            return Optional.of(Reason.TOP_REPOS_CHANGED);
        }
        if (!sameMembers(before.topLanguages(), current.topLanguages())) {
            return Optional.of(Reason.TOP_LANGUAGES_CHANGED);
        }
        return Optional.empty();
    }

    /** A class missing on one side counts as 0 there (a new class, or one dropped from the formula). */
    static boolean scoreMoved(Map<String, Integer> before, Map<String, Integer> now, int delta) {
        Set<String> classes = new HashSet<>(before.keySet());
        classes.addAll(now.keySet());
        return classes.stream().anyMatch(name ->
                Math.abs(now.getOrDefault(name, 0) - before.getOrDefault(name, 0)) >= delta);
    }

    /** Relative to the old value; from 0, any change counts (as if the old value were 1). */
    static boolean changed(int before, int now, double share) {
        return Math.abs(now - before) >= share * Math.max(before, 1) - 1e-9; // 10% of 100 is 10, not 10.000…02
    }

    /** Membership, not order: a reshuffle alone doesn't make the texts wrong. */
    static boolean sameMembers(List<String> before, List<String> now) {
        return Objects.equals(new HashSet<>(before), new HashSet<>(now));
    }

    private static LocalDate utcDay(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }
}
