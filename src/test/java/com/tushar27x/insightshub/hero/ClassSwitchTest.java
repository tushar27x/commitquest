package com.tushar27x.insightshub.hero;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.hero.ClassSwitch.Change;
import com.tushar27x.insightshub.hero.ClassSwitch.CoolingDown;
import com.tushar27x.insightshub.hero.ClassSwitch.Decision;
import com.tushar27x.insightshub.hero.ClassSwitch.NotQualified;
import com.tushar27x.insightshub.hero.ClassSwitch.Unchanged;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static com.tushar27x.insightshub.entity.Archetype.BARBARIAN;
import static com.tushar27x.insightshub.entity.Archetype.BARD;
import static com.tushar27x.insightshub.entity.Archetype.CLERIC;
import static com.tushar27x.insightshub.entity.Archetype.MONK;
import static com.tushar27x.insightshub.entity.Archetype.PALADIN;
import static com.tushar27x.insightshub.entity.Archetype.ROGUE;
import static com.tushar27x.insightshub.entity.Archetype.WIZARD;
import static org.assertj.core.api.Assertions.assertThat;

/** {@code PUT /hero/class} (plan §2 "Class switching", contract.md): qualify ≥ 40 or the top class, 7-day cooldown. */
class ClassSwitchTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:50:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-16");
    private static final Duration WEEK = Duration.ofDays(7);
    private static final int QUALIFY = 40;

    /** The developer's real calibration (plan §5): MONK on top, ROGUE/PALADIN/BARBARIAN also qualify. */
    private static Map<Archetype, Integer> scores() {
        Map<Archetype, Integer> scores = new EnumMap<>(Archetype.class);
        scores.putAll(Map.of(PALADIN, 44, BARBARIAN, 44, WIZARD, 33, Archetype.ARTIFICER, 33,
                CLERIC, 0, BARD, 10, MONK, 52, ROGUE, 47));
        return scores;
    }

    private static Decision decide(Archetype chosen, Instant changedAt, Archetype requested) {
        return ClassSwitch.decide(scores(), MONK, chosen, changedAt, requested, NOW, TODAY, QUALIFY, WEEK);
    }

    @Test
    void aQualifiedClassIsChosenAndTheEventSaysFromToAndChosen() {
        Decision decision = decide(null, null, ROGUE);

        assertThat(decision).isEqualTo(new Change(ROGUE, new HeroEvent(HeroEventType.CLASS_CHANGED, TODAY,
                details(MONK, ROGUE, ROGUE))));
    }

    @Test
    void theExactThresholdQualifies() {
        Map<Archetype, Integer> scores = scores();
        scores.put(WIZARD, QUALIFY);

        assertThat(ClassSwitch.decide(scores, MONK, null, null, WIZARD, NOW, TODAY, QUALIFY, WEEK))
                .isInstanceOf(Change.class);
    }

    @Test
    void belowTheThresholdAndNotTheTopClassIsNotQualified() {
        assertThat(decide(null, null, WIZARD)).isEqualTo(new NotQualified(WIZARD, 33));
        assertThat(decide(null, null, CLERIC)).isEqualTo(new NotQualified(CLERIC, 0));
    }

    @Test
    void theTopClassAlwaysQualifiesEvenBelowTheThreshold() {
        Map<Archetype, Integer> low = new EnumMap<>(Archetype.class);
        low.put(BARD, 12);

        assertThat(ClassSwitch.decide(low, BARD, ROGUE, null, BARD, NOW, TODAY, QUALIFY, WEEK))
                .isEqualTo(new Change(BARD, new HeroEvent(HeroEventType.CLASS_CHANGED, TODAY,
                        details(ROGUE, BARD, BARD))));
    }

    @Test
    void aMissingScoreCountsAsZero() {
        assertThat(ClassSwitch.decide(Map.of(), MONK, null, null, ROGUE, NOW, TODAY, QUALIFY, WEEK))
                .isEqualTo(new NotQualified(ROGUE, 0));
    }

    @Test
    void backToFollowingTheTopScoreAlwaysQualifies() {
        Map<Archetype, Integer> low = new EnumMap<>(Archetype.class);
        low.put(MONK, 5);

        assertThat(ClassSwitch.decide(low, MONK, BARD, null, null, NOW, TODAY, QUALIFY, WEEK))
                .isEqualTo(new Change(null, new HeroEvent(HeroEventType.CLASS_CHANGED, TODAY,
                        details(BARD, MONK, null))));
    }

    @Test
    void pinningTheClassAlreadyFollowedIsARealChange() {
        assertThat(decide(null, null, MONK)).isEqualTo(new Change(MONK, new HeroEvent(HeroEventType.CLASS_CHANGED,
                TODAY, details(MONK, MONK, MONK))));
    }

    @Test
    void theStoredChoiceAgainIsUnchangedEvenInTheCooldownOrBelowTheThreshold() {
        Instant yesterday = NOW.minus(Duration.ofDays(1));

        assertThat(decide(ROGUE, yesterday, ROGUE)).isEqualTo(new Unchanged());
        assertThat(decide(null, yesterday, null)).isEqualTo(new Unchanged());
        assertThat(decide(BARD, null, BARD)).isEqualTo(new Unchanged()); // BARD dropped to 10 since: kept
    }

    @Test
    void aChangeWithinSevenDaysWaitsForTheRestOfTheCooldown() {
        Instant changed = Instant.parse("2026-10-14T09:00:00Z");

        assertThat(decide(ROGUE, changed, PALADIN))
                .isEqualTo(new CoolingDown(Instant.parse("2026-10-21T09:00:00Z")));
        assertThat(decide(ROGUE, changed, null))
                .isEqualTo(new CoolingDown(Instant.parse("2026-10-21T09:00:00Z")));
    }

    @Test
    void theCooldownEndsExactlySevenDaysAfterTheChange() {
        assertThat(decide(ROGUE, NOW.minus(WEEK), PALADIN)).isInstanceOf(Change.class);
        assertThat(decide(ROGUE, NOW.minus(WEEK).plusSeconds(1), PALADIN))
                .isEqualTo(new CoolingDown(NOW.plusSeconds(1)));
    }

    @Test
    void notQualifiedComesBeforeTheCooldown() {
        // Waiting wouldn't help, so the permanent reason wins.
        assertThat(decide(ROGUE, NOW.minusSeconds(60), WIZARD)).isEqualTo(new NotQualified(WIZARD, 33));
    }

    @Test
    void retryAfterIsTheTimeLeft() {
        CoolingDown cooling = new CoolingDown(NOW.plus(Duration.ofHours(30)));

        assertThat(cooling.retryAfter(NOW)).isEqualTo(Duration.ofHours(30));
    }

    @Test
    void theChangeKeepsTheChosenClassAndChangesNothingElse() {
        Change change = (Change) decide(PALADIN, NOW.minus(Duration.ofDays(30)), ROGUE);

        assertThat(change.chosen()).isEqualTo(ROGUE);
        assertThat(change.event().details()).isEqualTo(details(PALADIN, ROGUE, ROGUE));
    }

    private static Map<String, Object> details(Archetype from, Archetype to, Archetype chosen) {
        Map<String, Object> details = new HashMap<>();
        details.put("from", from.name());
        details.put("to", to.name());
        details.put("chosen", chosen == null ? null : chosen.name());
        return details;
    }
}
