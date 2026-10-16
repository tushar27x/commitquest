package com.tushar27x.insightshub.hero;

import com.tushar27x.insightshub.hero.HeroRules.CatchUp;
import com.tushar27x.insightshub.hero.HeroRules.DayResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.tushar27x.insightshub.hero.HeroEventType.FAINTED;
import static com.tushar27x.insightshub.hero.HeroEventType.LEVEL_DOWN;
import static com.tushar27x.insightshub.hero.HeroEventType.LEVEL_UP;
import static com.tushar27x.insightshub.hero.HeroEventType.LIFE_GAINED;
import static com.tushar27x.insightshub.hero.HeroEventType.LIFE_LOST;
import static com.tushar27x.insightshub.hero.HeroEventType.RESTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** §2 of docs/v2-hero-plan.md, rule by rule. */
class HeroRulesTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 12);
    private static final LocalDate SUN = MON.minusDays(1);

    /** A hero settled up to Sunday 11 Oct. */
    private static HeroState hero(int level, int xp, int lives, int streak) {
        return new HeroState(level, xp, lives, streak, SUN);
    }

    private static HeroDay active(LocalDate date) {
        return new HeroDay(date, 1, 0, false, false);
    }

    private static HeroDay inactive(LocalDate date) {
        return new HeroDay(date, 0, 0, false, false);
    }

    private static HeroDay rest(LocalDate date, int contributions) {
        return new HeroDay(date, contributions, 0, true, false);
    }

    private static List<HeroEventType> types(DayResult result) {
        return result.events().stream().map(HeroEvent::type).toList();
    }

    @Nested
    class ActiveDay {

        @Test
        void givesTenXpAndExtendsTheStreak() {
            DayResult result = HeroRules.evaluateDay(hero(2, 20, 2, 4), active(MON));

            assertThat(result.state()).isEqualTo(new HeroState(2, 30, 2, 5, MON));
            assertThat(result.xpGained()).isEqualTo(10);
            assertThat(result.events()).isEmpty();
        }

        @Test
        void branchCommitsAloneCountAsActive() {
            DayResult result = HeroRules.evaluateDay(hero(1, 0, 3, 0), new HeroDay(MON, 0, 2, false, false));

            assertThat(result.xpGained()).isEqualTo(10);
            assertThat(result.state().streak()).isEqualTo(1);
        }

        @Test
        void everyThirdDayInARowWinsAHeartBack() {
            DayResult result = HeroRules.evaluateDay(hero(3, 0, 1, 2), active(MON));

            assertThat(result.state().lives()).isEqualTo(2);
            assertThat(result.state().streak()).isEqualTo(3);
            assertThat(result.events()).containsExactly(
                    new HeroEvent(LIFE_GAINED, MON, Map.of("lives", 2, "streak", 3)));
        }

        @Test
        void sixthDayWinsAnotherHeart() {
            assertThat(types(HeroRules.evaluateDay(hero(3, 0, 1, 5), active(MON)))).containsExactly(LIFE_GAINED);
        }

        @Test
        void daysBetweenTheThirdsWinNothing() {
            assertThat(HeroRules.evaluateDay(hero(3, 0, 1, 3), active(MON)).state().lives()).isEqualTo(1);
            assertThat(HeroRules.evaluateDay(hero(3, 0, 1, 4), active(MON)).state().lives()).isEqualTo(1);
        }

        @Test
        void atThreeHeartsTheThirdDayWinsNothingAndSaysNothing() {
            DayResult result = HeroRules.evaluateDay(hero(3, 0, 3, 2), active(MON));

            assertThat(result.state().lives()).isEqualTo(HeroRules.MAX_LIVES);
            assertThat(result.state().streak()).isEqualTo(3);
            assertThat(result.events()).isEmpty();
        }

        @Test
        void reachingTheThresholdLevelsUpAndCarriesTheRest() {
            // level 2 needs 100 XP: 95 + 10 = 105 → level 3 with 5
            DayResult result = HeroRules.evaluateDay(hero(2, 95, 3, 0), active(MON));

            assertThat(result.state().level()).isEqualTo(3);
            assertThat(result.state().xp()).isEqualTo(5);
            assertThat(result.events()).containsExactly(new HeroEvent(LEVEL_UP, MON, Map.of("from", 2, "to", 3)));
        }

        @Test
        void exactlyTheThresholdLevelsUpWithZeroLeft() {
            DayResult result = HeroRules.evaluateDay(hero(1, 40, 3, 0), active(MON));

            assertThat(result.state().level()).isEqualTo(2);
            assertThat(result.state().xp()).isZero();
        }

        @Test
        void levelUpAndHeartOnTheSameDay() {
            DayResult result = HeroRules.evaluateDay(hero(1, 45, 2, 2), active(MON));

            assertThat(types(result)).containsExactly(LEVEL_UP, LIFE_GAINED);
            assertThat(result.state()).isEqualTo(new HeroState(2, 5, 3, 3, MON));
        }

        @Test
        void levelsHaveNoCap() {
            int level = 1_000;
            DayResult result = HeroRules.evaluateDay(hero(level, HeroRules.xpForNext(level) - 10, 3, 0), active(MON));

            assertThat(result.state().level()).isEqualTo(level + 1);
        }
    }

    @Nested
    class RestDay {

        @Test
        void withoutActivityRestsAndPausesTheStreak() {
            DayResult result = HeroRules.evaluateDay(hero(2, 20, 2, 4), rest(MON, 0));

            assertThat(result.state()).isEqualTo(new HeroState(2, 20, 2, 4, MON));
            assertThat(result.xpGained()).isZero();
            assertThat(result.events()).containsExactly(new HeroEvent(RESTED, MON, Map.of()));
        }

        @Test
        void withActivityCountsAsActive() {
            DayResult result = HeroRules.evaluateDay(hero(2, 20, 2, 2), rest(MON, 3));

            assertThat(result.state()).isEqualTo(new HeroState(2, 30, 3, 3, MON));
            assertThat(types(result)).containsExactly(LIFE_GAINED);
        }

        @Test
        void aRestInTheMiddleDoesntBreakTheRunToTheNextHeart() {
            // active, active, rest, active: the 3rd active day wins the heart
            HeroState state = hero(1, 0, 2, 0);
            CatchUp run = HeroRules.evaluateDays(state, List.of(
                    active(MON), active(MON.plusDays(1)), rest(MON.plusDays(2), 0), active(MON.plusDays(3))));

            assertThat(run.state().streak()).isEqualTo(3);
            assertThat(run.state().lives()).isEqualTo(3);
            assertThat(run.events()).extracting(HeroEvent::type).containsExactly(RESTED, LIFE_GAINED);
        }

        @Test
        void twoRestDaysInARowBothRest() {
            CatchUp run = HeroRules.evaluateDays(hero(1, 0, 1, 1), List.of(rest(MON, 0), rest(MON.plusDays(1), 0)));

            assertThat(run.state().lives()).isEqualTo(1);
            assertThat(run.state().streak()).isEqualTo(1);
            assertThat(run.events()).extracting(HeroEvent::type).containsExactly(RESTED, RESTED);
        }
    }

    @Nested
    class MissedDay {

        @Test
        void costsAHeartAndBreaksTheStreak() {
            DayResult result = HeroRules.evaluateDay(hero(4, 70, 3, 8), inactive(MON));

            assertThat(result.state()).isEqualTo(new HeroState(4, 70, 2, 0, MON));
            assertThat(result.xpGained()).isZero();
            assertThat(result.events()).containsExactly(
                    new HeroEvent(LIFE_LOST, MON, Map.of("lives", 2, "reason", "NO_ACTIVITY")));
        }

        @Test
        void lastHeartLostDropsALevelAndRefillsTheHearts() {
            DayResult result = HeroRules.evaluateDay(hero(5, 180, 1, 0), inactive(MON));

            assertThat(result.state()).isEqualTo(new HeroState(4, 0, 3, 0, MON)); // XP back to the start of level 4
            assertThat(result.events()).containsExactly(
                    new HeroEvent(LIFE_LOST, MON, Map.of("lives", 0, "reason", "NO_ACTIVITY")),
                    new HeroEvent(LEVEL_DOWN, MON, Map.of("from", 5, "to", 4)));
        }

        @Test
        void lastHeartLostAtLevelOneFaintsInstead() {
            DayResult result = HeroRules.evaluateDay(hero(1, 30, 1, 0), inactive(MON));

            assertThat(result.state()).isEqualTo(new HeroState(1, 0, 3, 0, MON));
            assertThat(result.events()).containsExactly(
                    new HeroEvent(LIFE_LOST, MON, Map.of("lives", 0, "reason", "NO_ACTIVITY")),
                    new HeroEvent(FAINTED, MON, Map.of()));
        }

        @Test
        void afterAMissTheNextHeartNeedsThreeNewDays() {
            CatchUp run = HeroRules.evaluateDays(hero(2, 0, 3, 2), List.of(
                    inactive(MON), active(MON.plusDays(1)), active(MON.plusDays(2)), active(MON.plusDays(3))));

            assertThat(run.state().lives()).isEqualTo(3);
            assertThat(run.events()).extracting(HeroEvent::type).containsExactly(LIFE_LOST, LIFE_GAINED);
            assertThat(run.events().get(1).onDate()).isEqualTo(MON.plusDays(3));
        }
    }

    @Nested
    class SignUpDay {

        @Test
        void withoutActivityCostsNothing() {
            DayResult result = HeroRules.evaluateDay(hero(1, 0, 3, 0), new HeroDay(MON, 0, 0, false, true));

            assertThat(result.state()).isEqualTo(new HeroState(1, 0, 3, 0, MON));
            assertThat(result.events()).isEmpty();
        }

        @Test
        void withActivityCountsAsActive() {
            DayResult result = HeroRules.evaluateDay(hero(1, 0, 3, 0), new HeroDay(MON, 2, 0, false, true));

            assertThat(result.xpGained()).isEqualTo(10);
            assertThat(result.state().streak()).isEqualTo(1);
        }
    }

    @Nested
    class Order {

        @Test
        void anAlreadyEvaluatedDayChangesNothing() {
            HeroState settled = new HeroState(2, 20, 2, 4, MON);

            DayResult again = HeroRules.evaluateDay(settled, inactive(MON));
            DayResult older = HeroRules.evaluateDay(settled, inactive(SUN));

            assertThat(again.state()).isEqualTo(settled);
            assertThat(again.events()).isEmpty();
            assertThat(older.state()).isEqualTo(settled);
        }

        @Test
        void skippingADayIsRefused() {
            assertThatThrownBy(() -> HeroRules.evaluateDay(hero(1, 0, 3, 0), active(MON.plusDays(1))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2026-10-12");
        }

        @Test
        void catchUpAppliesMissedDaysInOrderAndKeepsEachDaysResult() {
            CatchUp run = HeroRules.evaluateDays(hero(1, 0, 3, 0), List.of(
                    active(MON), inactive(MON.plusDays(1)), inactive(MON.plusDays(2)), active(MON.plusDays(3))));

            assertThat(run.state()).isEqualTo(new HeroState(1, 20, 1, 1, MON.plusDays(3)));
            assertThat(run.days()).extracting(DayResult::xpGained).containsExactly(10, 0, 0, 10);
            assertThat(run.days()).extracting(day -> day.state().streak()).containsExactly(1, 0, 0, 1);
            assertThat(run.events()).extracting(HeroEvent::onDate)
                    .containsExactly(MON.plusDays(1), MON.plusDays(2));
        }

        @Test
        void catchUpOfAWholeWeekOffFaintsAndKeepsGoing() {
            List<HeroDay> week = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                week.add(inactive(MON.plusDays(i)));
            }
            CatchUp run = HeroRules.evaluateDays(hero(2, 10, 3, 5), week);

            // 3 misses: level 2 → 1, hearts refill; 3 more: faint at level 1; the 7th: 2 hearts left
            assertThat(run.events()).extracting(HeroEvent::type).containsExactly(
                    LIFE_LOST, LIFE_LOST, LIFE_LOST, LEVEL_DOWN,
                    LIFE_LOST, LIFE_LOST, LIFE_LOST, FAINTED,
                    LIFE_LOST);
            assertThat(run.state()).isEqualTo(new HeroState(1, 0, 2, 0, MON.plusDays(6)));
        }

        @Test
        void catchUpRunTwiceIsHarmless() {
            List<HeroDay> days = List.of(active(MON), inactive(MON.plusDays(1)));
            CatchUp first = HeroRules.evaluateDays(hero(1, 0, 3, 0), days);

            CatchUp second = HeroRules.evaluateDays(first.state(), days);

            assertThat(second.state()).isEqualTo(first.state());
            assertThat(second.events()).isEmpty();
            assertThat(second.days()).isEmpty();
        }

        @Test
        void catchUpCrossesMonthAndYearBoundaries() {
            LocalDate dec30 = LocalDate.of(2026, 12, 30);
            HeroState state = new HeroState(1, 0, 3, 0, dec30.minusDays(1));

            CatchUp run = HeroRules.evaluateDays(state, List.of(
                    active(dec30), active(dec30.plusDays(1)), active(LocalDate.of(2027, 1, 1))));

            assertThat(run.state().lastEvaluatedOn()).isEqualTo(LocalDate.of(2027, 1, 1));
            assertThat(run.state().streak()).isEqualTo(3);
        }

        @Test
        void catchUpWithAGapIsRefused() {
            assertThatThrownBy(() -> HeroRules.evaluateDays(hero(1, 0, 3, 0), List.of(active(MON), active(MON.plusDays(2)))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Levels {

        @Test
        void levelNNeedsFiftyTimesNXp() {
            assertThat(HeroRules.xpForNext(1)).isEqualTo(50);
            assertThat(HeroRules.xpForNext(7)).isEqualTo(350);
        }

        @Test
        void stateRejectsImpossibleValues() {
            assertThatThrownBy(() -> new HeroState(0, 0, 3, 0, MON)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new HeroState(1, 50, 3, 0, MON)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new HeroState(1, -1, 3, 0, MON)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new HeroState(1, 0, 0, 0, MON)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new HeroState(1, 0, 4, 0, MON)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new HeroState(1, 0, 3, -1, MON)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new HeroState(1, 0, 3, 0, null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class FirstLogin {

        private final LocalDate yesterday = LocalDate.of(2026, 10, 15);

        @Test
        void seedsTenXpPerActiveDayWithFullHeartsAndNoEvents() {
            // 12 active days = 120 XP: level 1 takes 50, level 2 takes 70 of 100
            List<LocalDate> active = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                active.add(yesterday.minusDays(2L * i + 5)); // spread out: no trailing streak
            }

            HeroState seeded = HeroRules.seed(active, yesterday);

            assertThat(seeded).isEqualTo(new HeroState(2, 70, 3, 0, yesterday));
        }

        @Test
        void onlyTheLastNinetyDaysCount() {
            List<LocalDate> active = List.of(
                    yesterday.minusDays(HeroRules.SEED_DAYS - 1), // day 90: counted
                    yesterday.minusDays(HeroRules.SEED_DAYS),     // day 91: not
                    yesterday.plusDays(1));                       // after the seed window: not

            assertThat(HeroRules.seed(active, yesterday).xp()).isEqualTo(10);
        }

        @Test
        void aFullNinetyDaysSeedsLevelSix() {
            List<LocalDate> active = new ArrayList<>();
            for (int i = 0; i < HeroRules.SEED_DAYS; i++) {
                active.add(yesterday.minusDays(i));
            }
            // 900 XP: levels 1-5 take 50+100+150+200+250 = 750, 150 of level 6's 300 left
            HeroState seeded = HeroRules.seed(active, yesterday);

            assertThat(seeded.level()).isEqualTo(6);
            assertThat(seeded.xp()).isEqualTo(150);
            assertThat(seeded.streak()).isEqualTo(HeroRules.SEED_DAYS);
        }

        @Test
        void theStreakIsTheRunEndingYesterday() {
            List<LocalDate> active = List.of(yesterday, yesterday.minusDays(1), yesterday.minusDays(3));

            assertThat(HeroRules.seed(active, yesterday).streak()).isEqualTo(2);
            assertThat(HeroRules.seed(List.of(yesterday.minusDays(1)), yesterday).streak()).isZero();
        }

        @Test
        void duplicatesCountOnce() {
            assertThat(HeroRules.seed(List.of(yesterday, yesterday), yesterday).xp()).isEqualTo(10);
        }

        @Test
        void aNewAccountStartsAtLevelOne() {
            assertThat(HeroRules.seed(List.of(), yesterday)).isEqualTo(new HeroState(1, 0, 3, 0, yesterday));
        }
    }
}
