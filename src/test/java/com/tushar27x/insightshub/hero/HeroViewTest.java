package com.tushar27x.insightshub.hero;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.hero.HeroView.ClassView;
import com.tushar27x.insightshub.hero.HeroView.WeekDay;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.tushar27x.insightshub.entity.Archetype.BARBARIAN;
import static com.tushar27x.insightshub.entity.Archetype.BARD;
import static com.tushar27x.insightshub.entity.Archetype.MONK;
import static com.tushar27x.insightshub.entity.Archetype.PALADIN;
import static com.tushar27x.insightshub.entity.Archetype.ROGUE;
import static com.tushar27x.insightshub.entity.Archetype.WIZARD;
import static com.tushar27x.insightshub.hero.HeroEventType.FAINTED;
import static com.tushar27x.insightshub.hero.HeroEventType.LEVEL_DOWN;
import static com.tushar27x.insightshub.hero.HeroEventType.LEVEL_UP;
import static com.tushar27x.insightshub.hero.HeroEventType.LIFE_GAINED;
import static com.tushar27x.insightshub.hero.HeroEventType.LIFE_LOST;
import static com.tushar27x.insightshub.hero.HeroEventType.RESTED;
import static org.assertj.core.api.Assertions.assertThat;

/** What GET /hero derives from the stored hero: mood, tier, the week strip and the class block (contract.md). */
class HeroViewTest {

    private static final LocalTime MORNING = LocalTime.of(9, 0);
    private static final LocalTime EVENING = LocalTime.of(18, 20);

    // --- base mood and tier ---

    @Test
    void baseMoodComesFromLivesOnly() {
        assertThat(HeroView.baseMood(3)).isEqualTo(Mood.HAPPY);
        assertThat(HeroView.baseMood(2)).isEqualTo(Mood.OKAY);
        assertThat(HeroView.baseMood(1)).isEqualTo(Mood.WEAK);
    }

    @Test
    void tierChangesAtLevelsFiveTenAndFifteen() {
        assertThat(HeroView.tier(1)).isEqualTo(Tier.CAMPFIRE);
        assertThat(HeroView.tier(4)).isEqualTo(Tier.CAMPFIRE);
        assertThat(HeroView.tier(5)).isEqualTo(Tier.VILLAGE);
        assertThat(HeroView.tier(9)).isEqualTo(Tier.VILLAGE);
        assertThat(HeroView.tier(10)).isEqualTo(Tier.KEEP);
        assertThat(HeroView.tier(14)).isEqualTo(Tier.KEEP);
        assertThat(HeroView.tier(15)).isEqualTo(Tier.CITADEL);
        assertThat(HeroView.tier(99)).isEqualTo(Tier.CITADEL);
    }

    // --- mood ---

    @Test
    void moodIsTheBaseMoodDuringTheDay() {
        assertThat(HeroView.mood(3, Set.of(), false, false, MORNING)).isEqualTo(Mood.HAPPY);
        assertThat(HeroView.mood(2, Set.of(LIFE_LOST), false, false, MORNING)).isEqualTo(Mood.OKAY);
        assertThat(HeroView.mood(1, Set.of(), false, true, EVENING)).isEqualTo(Mood.WEAK);
    }

    @Test
    void notFedAfterSixInTheEveningIsHungry() {
        assertThat(HeroView.mood(2, Set.of(), false, false, LocalTime.of(17, 59, 59))).isEqualTo(Mood.OKAY);
        assertThat(HeroView.mood(2, Set.of(), false, false, LocalTime.of(18, 0))).isEqualTo(Mood.HUNGRY);
        assertThat(HeroView.mood(3, Set.of(), false, true, EVENING)).isEqualTo(Mood.HAPPY); // fed
    }

    @Test
    void anUnfedRestDayIsSleepingEvenInTheEvening() {
        assertThat(HeroView.mood(3, Set.of(), true, false, MORNING)).isEqualTo(Mood.SLEEPING);
        assertThat(HeroView.mood(1, Set.of(), true, false, EVENING)).isEqualTo(Mood.SLEEPING);
        assertThat(HeroView.mood(3, Set.of(), true, true, EVENING)).isEqualTo(Mood.HAPPY); // worked anyway
    }

    @Test
    void aLevelUpOnTheLastSettledDayShowsUntilTheNextEvaluation() {
        assertThat(HeroView.mood(3, Set.of(LEVEL_UP, LIFE_GAINED), false, false, EVENING)).isEqualTo(Mood.LEVEL_UP);
        assertThat(HeroView.mood(3, Set.of(LEVEL_UP), true, false, MORNING)).isEqualTo(Mood.LEVEL_UP);
    }

    @Test
    void hittingZeroHeartsShowsFaintedForALevelDownToo() {
        assertThat(HeroView.mood(3, Set.of(LIFE_LOST, FAINTED), false, false, MORNING)).isEqualTo(Mood.FAINTED);
        assertThat(HeroView.mood(3, Set.of(LIFE_LOST, LEVEL_DOWN), false, false, EVENING)).isEqualTo(Mood.FAINTED);
        assertThat(HeroView.mood(3, Set.of(RESTED), false, false, MORNING)).isEqualTo(Mood.HAPPY);
    }

    // --- week strip ---

    /** The fixture story: Fri 16 Oct 2026, Mon/Tue active, Wed missed, Thu active, Sunday a rest day. */
    @Test
    void weekMatchesTheFixtureStory() {
        LocalDate friday = LocalDate.of(2026, 10, 16);
        Set<LocalDate> active = Set.of(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 15));

        assertThat(HeroView.week(friday, active::contains, date -> date.getDayOfWeek() == DayOfWeek.SUNDAY))
                .extracting(WeekDay::status)
                .containsExactly(WeekStatus.ACTIVE, WeekStatus.ACTIVE, WeekStatus.MISSED, WeekStatus.ACTIVE,
                        WeekStatus.TODAY, WeekStatus.FUTURE, WeekStatus.REST);
        assertThat(HeroView.week(friday, active::contains, date -> false))
                .extracting(WeekDay::date)
                .startsWith(LocalDate.of(2026, 10, 12))
                .endsWith(LocalDate.of(2026, 10, 18));
    }

    @Test
    void weekRunsMondayToSundayAcrossAMonthBoundary() {
        LocalDate thursday = LocalDate.of(2026, 10, 1);

        assertThat(HeroView.week(thursday, date -> false, date -> false))
                .extracting(WeekDay::date)
                .containsExactly(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30),
                        thursday, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4));
    }

    @Test
    void pastRestDaysAreRestUnlessActiveAndTodayIsRestOnARestDay() {
        LocalDate sunday = LocalDate.of(2026, 10, 18);
        LocalDate saturday = sunday.minusDays(1);
        LocalDate monday = LocalDate.of(2026, 10, 12);

        var week = HeroView.week(sunday, date -> date.equals(monday),
                date -> date.equals(monday) || date.equals(saturday) || date.equals(sunday));

        assertThat(week.get(0).status()).isEqualTo(WeekStatus.ACTIVE); // a rest day with activity counts as active
        assertThat(week.get(5).status()).isEqualTo(WeekStatus.REST);
        assertThat(week.get(6).status()).isEqualTo(WeekStatus.REST); // today, but a rest day
    }

    @Test
    void mondayIsTodayAndTheRestIsFuture() {
        LocalDate monday = LocalDate.of(2026, 10, 12);

        assertThat(HeroView.week(monday, date -> true, date -> date.getDayOfWeek() == DayOfWeek.WEDNESDAY))
                .extracting(WeekDay::status)
                .containsExactly(WeekStatus.TODAY, WeekStatus.FUTURE, WeekStatus.REST, WeekStatus.FUTURE,
                        WeekStatus.FUTURE, WeekStatus.FUTURE, WeekStatus.FUTURE);
    }

    // --- class ---

    /** The developer's real calibration (plan §5). */
    private static Map<Archetype, Integer> fixtureScores() {
        Map<Archetype, Integer> scores = new EnumMap<>(Archetype.class);
        scores.putAll(Map.of(PALADIN, 44, BARBARIAN, 44, WIZARD, 33, Archetype.ARTIFICER, 33,
                Archetype.CLERIC, 0, BARD, 10, MONK, 52, ROGUE, 47));
        return scores;
    }

    private static final Instant NOW = Instant.parse("2026-10-16T12:50:00Z");
    private static final Duration WEEK = Duration.ofDays(7);

    @Test
    void qualifiedClassesScoreAtLeast40SortedByScoreThenDeclarationOrder() {
        ClassView view = HeroView.classView(fixtureScores(), MONK, null, null, NOW, 40, WEEK);

        assertThat(view.chosen()).isNull();
        assertThat(view.current()).isEqualTo(MONK);
        assertThat(view.scored()).isEqualTo(MONK);
        assertThat(view.qualified()).containsExactly(MONK, ROGUE, PALADIN, BARBARIAN);
        assertThat(view.canSwitchAt()).isNull();
    }

    @Test
    void theTopClassQualifiesEvenBelowTheThreshold() {
        Map<Archetype, Integer> low = new EnumMap<>(Archetype.class);
        low.put(BARD, 12);
        low.put(WIZARD, 5);

        assertThat(HeroView.classView(low, BARD, null, null, NOW, 40, WEEK).qualified()).containsExactly(BARD);
    }

    @Test
    void theChosenClassIsCurrentAndTheCooldownRunsSevenDaysFromTheChange() {
        Instant changed = Instant.parse("2026-10-14T09:00:00Z");

        ClassView view = HeroView.classView(fixtureScores(), MONK, ROGUE, changed, NOW, 40, WEEK);

        assertThat(view.chosen()).isEqualTo(ROGUE);
        assertThat(view.current()).isEqualTo(ROGUE);
        assertThat(view.scored()).isEqualTo(MONK);
        assertThat(view.canSwitchAt()).isEqualTo(Instant.parse("2026-10-21T09:00:00Z"));
    }

    @Test
    void anExpiredCooldownIsNull() {
        Instant changed = NOW.minus(WEEK);

        assertThat(HeroView.classView(fixtureScores(), MONK, ROGUE, changed, NOW, 40, WEEK).canSwitchAt()).isNull();
        assertThat(HeroView.classView(fixtureScores(), MONK, ROGUE, changed.plusSeconds(1), NOW, 40, WEEK).canSwitchAt())
                .isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void aChosenClassThatDroppedBelowTheThresholdIsKept() {
        ClassView view = HeroView.classView(fixtureScores(), MONK, BARD, null, NOW, 40, WEEK);

        assertThat(view.current()).isEqualTo(BARD);
        assertThat(view.qualified()).doesNotContain(BARD);
    }

    @Test
    void missingScoresCountAsZero() {
        assertThat(HeroView.classView(Map.of(), ROGUE, null, null, NOW, 40, WEEK).qualified()).containsExactly(ROGUE);
    }
}
