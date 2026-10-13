package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.entity.Archetype;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Scores every archetype 0–100 from a {@link HabitProfile}; the highest score is the primary archetype.
 * <p>
 * Each score is a weighted sum (weights add up to 100) of signals scaled between a floor and a cap
 * by {@link #scale}. The caps are fixed for now; with more users they could become percentiles.
 * Bump {@link #ALGO_VERSION} whenever a weight, floor or cap changes.
 * <p>
 * Ties go to the archetype declared first in {@link Archetype}. A user with no visible activity at all
 * scores 0 everywhere and becomes a {@link Archetype#ROGUE}: the one who leaves no trace.
 */
@Component
public class ScoringArchetypeClassifier implements ArchetypeClassifier {

    static final int ALGO_VERSION = 1;

    @Override
    public ArchetypeScores classify(HabitProfile h) {
        Map<Archetype, Integer> scores = new EnumMap<>(Archetype.class);
        scores.put(Archetype.PALADIN, score(
                60 * scale(h.activeDayRatio(), 0.1, 0.7),
                40 * scale(h.longestStreak(), 3, 30)));
        scores.put(Archetype.BARBARIAN, score(
                50 * scale(h.avgPrSize(), 300, 3000),
                50 * scale(h.burstiness(), 3, 10)));
        scores.put(Archetype.WIZARD, score(
                100 * scale(h.languageCount(), 2, 8)));
        scores.put(Archetype.ARTIFICER, score(
                100 * scale(h.infraRepoShare(), 0, 0.6)));
        scores.put(Archetype.CLERIC, score(
                50 * scale(h.issueShare(), 0.05, 0.3),
                50 * scale(h.fixCommitShare(), 0.1, 0.5)));
        scores.put(Archetype.BARD, score(
                40 * scale(h.prShare(), 0.1, 0.5),
                30 * scale(Math.log10(h.audience() + 1), 1, 3), // ~10 to ~1,000 followers + stars
                30 * scale(h.documentedRepoShare(), 0.2, 0.8)));
        scores.put(Archetype.MONK, score(
                60 * scale(h.descriptiveCommitShare(), 0.4, 0.9),
                // small PRs only count when there are PRs at all
                h.prCount() == 0 ? 0 : 40 * (1 - scale(h.avgPrSize(), 100, 800))));
        scores.put(Archetype.ROGUE, score(
                // weekends are 2/7 ≈ 0.29 of all days, so an even spread scores 0
                100 * scale(h.weekendShare(), 0.3, 0.6)));

        return new ArchetypeScores(primary(scores), scores, ALGO_VERSION);
    }

    private static Archetype primary(Map<Archetype, Integer> scores) {
        if (scores.values().stream().allMatch(score -> score == 0)) {
            return Archetype.ROGUE;
        }
        Archetype best = null;
        for (Archetype archetype : Archetype.values()) { // declaration order, so the first one wins a tie
            if (best == null || scores.get(archetype) > scores.get(best)) {
                best = archetype;
            }
        }
        return best;
    }

    /** 0 at or below {@code floor}, 1 at or above {@code cap}, linear in between. */
    static double scale(double value, double floor, double cap) {
        return Math.clamp((value - floor) / (cap - floor), 0.0, 1.0);
    }

    private static int score(double... parts) {
        return (int) Math.round(Arrays.stream(parts).sum());
    }
}
