package com.tushar27x.insightshub.hero;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Which days are ready to settle: a day is evaluated once, after it ends, with a 6-hour grace (§2). */
class DueDaysTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final LocalDate OCT_14 = LocalDate.of(2026, 10, 14);
    private static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);

    @Test
    void yesterdayIsDueFromSixInTheMorning() {
        assertThat(HeroRules.dueDays(OCT_14, Instant.parse("2026-10-16T05:59:59Z"), ZoneOffset.UTC)).isEmpty();
        assertThat(HeroRules.dueDays(OCT_14, Instant.parse("2026-10-16T06:00:00Z"), ZoneOffset.UTC))
                .containsExactly(OCT_15);
    }

    @Test
    void graceRunsOnLocalTime() {
        // 06:00 on Oct 16 in Kolkata is 00:30 UTC
        assertThat(HeroRules.dueDays(OCT_14, Instant.parse("2026-10-16T00:29:59Z"), KOLKATA)).isEmpty();
        assertThat(HeroRules.dueDays(OCT_14, Instant.parse("2026-10-16T00:30:00Z"), KOLKATA)).containsExactly(OCT_15);
        // 06:00 on Oct 16 in Los Angeles (PDT) is 13:00 UTC
        assertThat(HeroRules.dueDays(OCT_14, Instant.parse("2026-10-16T12:59:59Z"), LOS_ANGELES)).isEmpty();
        assertThat(HeroRules.dueDays(OCT_14, Instant.parse("2026-10-16T13:00:00Z"), LOS_ANGELES)).containsExactly(OCT_15);
    }

    @Test
    void theSameInstantIsADifferentDayAcrossZones() {
        Instant now = Instant.parse("2026-10-16T03:00:00Z"); // 08:30 Oct 16 Kolkata, 20:00 Oct 15 Los Angeles

        assertThat(HeroRules.dueDays(OCT_14, now, KOLKATA)).containsExactly(OCT_15);
        assertThat(HeroRules.dueDays(LocalDate.of(2026, 10, 13), now, LOS_ANGELES)).containsExactly(OCT_14);
    }

    @Test
    void missedDaysAreAllDueOldestFirst() {
        assertThat(HeroRules.dueDays(LocalDate.of(2026, 10, 11), Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC))
                .containsExactly(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), OCT_14, OCT_15);
    }

    @Test
    void acrossAMonthBoundary() {
        assertThat(HeroRules.dueDays(LocalDate.of(2026, 10, 30), Instant.parse("2026-11-01T07:00:00Z"), ZoneOffset.UTC))
                .containsExactly(LocalDate.of(2026, 10, 31));
    }

    @Test
    void graceOnTheMorningAfterDstEnds() {
        // Nov 1 2026: LA falls back at 02:00, so 06:00 PST on Nov 2 is 14:00 UTC
        LocalDate nov1 = LocalDate.of(2026, 11, 1);
        assertThat(HeroRules.dueDays(nov1.minusDays(1), Instant.parse("2026-11-02T13:59:59Z"), LOS_ANGELES)).isEmpty();
        assertThat(HeroRules.dueDays(nov1.minusDays(1), Instant.parse("2026-11-02T14:00:00Z"), LOS_ANGELES))
                .containsExactly(nov1);
    }

    @Test
    void nothingDueWhenSettledOrAhead() {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");

        assertThat(HeroRules.dueDays(OCT_15, now, ZoneOffset.UTC)).isEmpty();
        assertThat(HeroRules.dueDays(LocalDate.of(2026, 10, 20), now, ZoneOffset.UTC)).isEmpty(); // zone moved east
    }
}
