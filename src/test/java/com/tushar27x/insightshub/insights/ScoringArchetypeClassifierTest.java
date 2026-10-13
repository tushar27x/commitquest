package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.entity.Archetype;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringArchetypeClassifierTest {

    private final ScoringArchetypeClassifier classifier = new ScoringArchetypeClassifier();

    /** Mutable stand-in for {@link HabitProfile}, so each test sets only the signals it cares about. */
    static final class Habits {
        double activeDayRatio;
        int longestStreak;
        double weekendShare;
        double burstiness;
        int languageCount;
        double infraRepoShare;
        double issueShare;
        double prShare;
        int audience;
        double documentedRepoShare;
        int prCount;
        double avgPrSize;
        int ownCommitCount;
        double fixCommitShare;
        double descriptiveCommitShare;

        static HabitProfile with(Consumer<Habits> setup) {
            Habits h = new Habits();
            setup.accept(h);
            return new HabitProfile(h.activeDayRatio, h.longestStreak, h.weekendShare, h.burstiness,
                    h.languageCount, h.infraRepoShare, h.issueShare, h.prShare, h.audience,
                    h.documentedRepoShare, h.prCount, h.avgPrSize, h.ownCommitCount, h.fixCommitShare,
                    h.descriptiveCommitShare);
        }
    }

    /** A clear-cut user for each class: that class maxes out and wins. */
    private static HabitProfile typical(Archetype archetype) {
        return switch (archetype) {
            case PALADIN -> Habits.with(h -> {
                h.activeDayRatio = 0.8;
                h.longestStreak = 45;
            });
            case BARBARIAN -> Habits.with(h -> {
                h.prCount = 5;
                h.avgPrSize = 4000;
                h.burstiness = 12;
            });
            case WIZARD -> Habits.with(h -> h.languageCount = 9);
            case ARTIFICER -> Habits.with(h -> h.infraRepoShare = 0.6);
            case CLERIC -> Habits.with(h -> {
                h.issueShare = 0.4;
                h.ownCommitCount = 10;
                h.fixCommitShare = 0.6;
            });
            case BARD -> Habits.with(h -> {
                h.prShare = 0.6;
                h.audience = 2000;
                h.documentedRepoShare = 1.0;
            });
            case MONK -> Habits.with(h -> {
                h.ownCommitCount = 10;
                h.descriptiveCommitShare = 1.0;
                h.prCount = 5;
                h.avgPrSize = 50;
            });
            case ROGUE -> Habits.with(h -> h.weekendShare = 0.7);
        };
    }

    @ParameterizedTest
    @EnumSource(Archetype.class)
    void typicalUserOfEachClassGetsThatClassWithFullScore(Archetype archetype) {
        ArchetypeScores result = classifier.classify(typical(archetype));

        assertThat(result.primary()).isEqualTo(archetype);
        assertThat(result.scores().get(archetype)).isEqualTo(100);
    }

    @Test
    void scoresEveryClassInDeclarationOrderAndCarriesTheAlgoVersion() {
        ArchetypeScores result = classifier.classify(typical(Archetype.BARD));

        assertThat(result.scores().keySet()).containsExactly(Archetype.values());
        assertThat(result.scores().values()).allSatisfy(score -> assertThat(score).isBetween(0, 100));
        assertThat(result.algoVersion()).isEqualTo(ScoringArchetypeClassifier.ALGO_VERSION);
    }

    @Test
    void noVisibleActivityIsARogueWithAllZeroScores() {
        ArchetypeScores result = classifier.classify(Habits.with(h -> {
        }));

        assertThat(result.scores().values()).containsOnly(0);
        assertThat(result.primary()).isEqualTo(Archetype.ROGUE);
    }

    @Test
    void tieGoesToTheClassDeclaredFirst() {
        ArchetypeScores result = classifier.classify(Habits.with(h -> {
            h.activeDayRatio = 0.6;  // Paladin: 60 * (0.5 / 0.6) = 50
            h.languageCount = 5;     // Wizard: 100 * (3 / 6) = 50
        }));

        assertThat(result.scores().get(Archetype.PALADIN)).isEqualTo(50);
        assertThat(result.scores().get(Archetype.WIZARD)).isEqualTo(50);
        assertThat(result.primary()).isEqualTo(Archetype.PALADIN);
    }

    @Test
    void evenWeekdayWeekendSpreadIsNotARogue() {
        ArchetypeScores result = classifier.classify(Habits.with(h -> h.weekendShare = 2 / 7.0));

        assertThat(result.scores().get(Archetype.ROGUE)).isZero();
    }

    @Test
    void monkGetsNoSmallPrCreditWithoutPullRequests() {
        // avgPrSize is 0 when there are no PRs; that must not read as "tiny PRs"
        ArchetypeScores result = classifier.classify(Habits.with(h -> h.prCount = 0));

        assertThat(result.scores().get(Archetype.MONK)).isZero();
    }

    @Test
    void hugePullRequestsCountForBarbarianAndAgainstMonk() {
        ArchetypeScores result = classifier.classify(Habits.with(h -> {
            h.prCount = 5;
            h.avgPrSize = 3000;
            h.ownCommitCount = 10;
            h.descriptiveCommitShare = 1.0;
        }));

        assertThat(result.scores().get(Archetype.BARBARIAN)).isEqualTo(50);
        assertThat(result.scores().get(Archetype.MONK)).isEqualTo(60);  // messages only, no small-PR part
    }

    @Test
    void scaleIsLinearBetweenFloorAndCap() {
        assertThat(ScoringArchetypeClassifier.scale(1, 2, 8)).isZero();
        assertThat(ScoringArchetypeClassifier.scale(2, 2, 8)).isZero();
        assertThat(ScoringArchetypeClassifier.scale(5, 2, 8)).isEqualTo(0.5);
        assertThat(ScoringArchetypeClassifier.scale(8, 2, 8)).isEqualTo(1.0);
        assertThat(ScoringArchetypeClassifier.scale(50, 2, 8)).isEqualTo(1.0);
    }
}
