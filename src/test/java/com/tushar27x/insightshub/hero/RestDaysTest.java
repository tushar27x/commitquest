package com.tushar27x.insightshub.hero;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestDaysTest {

    private static final LocalDate SAT_OCT_17 = LocalDate.of(2026, 10, 17);

    @Test
    void mondayIsBitZeroAndSundayBitSix() {
        assertThat(RestDays.toMask(Set.of(MONDAY))).isEqualTo(1);
        assertThat(RestDays.toMask(Set.of(SUNDAY))).isEqualTo(64);
        assertThat(RestDays.toMask(Set.of(SATURDAY, SUNDAY))).isEqualTo(96);
        assertThat(RestDays.toMask(Set.of())).isZero();
    }

    @Test
    void roundTripsInWeekOrder() {
        assertThat(RestDays.toDays(96)).containsExactly(SATURDAY, SUNDAY);
        assertThat(RestDays.toDays(RestDays.toMask(EnumSet.of(SUNDAY, MONDAY)))).containsExactly(MONDAY, SUNDAY);
        assertThat(RestDays.toDays(0)).isEmpty();
    }

    @Test
    void atMostTwo() {
        assertThatThrownBy(() -> RestDays.toMask(Set.of(FRIDAY, SATURDAY, SUNDAY)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RestDays.toDays(7)).isInstanceOf(IllegalArgumentException.class); // Mon-Wed
        assertThatThrownBy(() -> RestDays.toDays(128)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RestDays.toDays(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isRestDayReadsTheDatesWeekday() {
        int weekend = RestDays.toMask(Set.of(SATURDAY, SUNDAY));

        assertThat(RestDays.isRestDay(weekend, SAT_OCT_17)).isTrue();
        assertThat(RestDays.isRestDay(weekend, SAT_OCT_17.plusDays(1))).isTrue();
        assertThat(RestDays.isRestDay(weekend, SAT_OCT_17.plusDays(2))).isFalse();
        assertThat(RestDays.isRestDay(0, SAT_OCT_17)).isFalse();
    }

    @Test
    void aChangeAppliesFromItsDateNeverBefore() {
        int before = RestDays.toMask(Set.of(SUNDAY));
        int after = RestDays.toMask(Set.of(SATURDAY));
        LocalDate from = SAT_OCT_17; // changed on Friday, applies from Saturday

        assertThat(RestDays.maskOn(SAT_OCT_17.minusDays(1), after, before, from)).isEqualTo(before);
        assertThat(RestDays.maskOn(SAT_OCT_17, after, before, from)).isEqualTo(after);
        assertThat(RestDays.maskOn(SAT_OCT_17.plusDays(30), after, before, from)).isEqualTo(after);
    }

    @Test
    void neverChangedMeansTheCurrentValue() {
        assertThat(RestDays.maskOn(SAT_OCT_17, 64, 0, null)).isEqualTo(64);
    }

    @Test
    void everyValidMaskHasAtMostTwoBits() {
        // Mirrors the DB CHECK in V5: clearing the lowest set bit twice leaves 0
        for (int mask = 0; mask < 128; mask++) {
            int cleared = mask & (mask - 1);
            boolean dbAccepts = (cleared & (cleared - 1)) == 0;
            assertThat(dbAccepts).as("mask %d", mask).isEqualTo(Integer.bitCount(mask) <= 2);
        }
    }
}
