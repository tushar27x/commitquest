package com.tushar27x.insightshub.sync;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DayWindowTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");

    @Test
    void utcDayRunsFromMidnightToTheLastSecond() {
        DayWindow window = DayWindow.of(LocalDate.of(2026, 10, 15), ZoneOffset.UTC);

        assertThat(window.since()).isEqualTo(Instant.parse("2026-10-15T00:00:00Z"));
        assertThat(window.until()).isEqualTo(Instant.parse("2026-10-15T23:59:59Z"));
    }

    @Test
    void kolkataDayStartsTheEveningBeforeInUtc() {
        DayWindow window = DayWindow.of(LocalDate.of(2026, 10, 15), KOLKATA);

        assertThat(window.since()).isEqualTo(Instant.parse("2026-10-14T18:30:00Z"));
        assertThat(window.until()).isEqualTo(Instant.parse("2026-10-15T18:29:59Z"));
    }

    @Test
    void losAngelesDayEndsTheMorningAfterInUtc() {
        DayWindow window = DayWindow.of(LocalDate.of(2026, 10, 15), LOS_ANGELES); // PDT, UTC-7

        assertThat(window.since()).isEqualTo(Instant.parse("2026-10-15T07:00:00Z"));
        assertThat(window.until()).isEqualTo(Instant.parse("2026-10-16T06:59:59Z"));
    }

    @Test
    void dstStartDayIsTwentyThreeHoursLong() {
        DayWindow window = DayWindow.of(LocalDate.of(2026, 3, 8), LOS_ANGELES);

        assertThat(window.since()).isEqualTo(Instant.parse("2026-03-08T08:00:00Z")); // PST, UTC-8
        assertThat(window.until()).isEqualTo(Instant.parse("2026-03-09T06:59:59Z")); // PDT, UTC-7
        assertThat(Duration.between(window.since(), window.until()).plusSeconds(1)).isEqualTo(Duration.ofHours(23));
    }

    @Test
    void dstEndDayIsTwentyFiveHoursLong() {
        DayWindow window = DayWindow.of(LocalDate.of(2026, 11, 1), LOS_ANGELES);

        assertThat(Duration.between(window.since(), window.until()).plusSeconds(1)).isEqualTo(Duration.ofHours(25));
    }

    @Test
    void monthAndYearBoundaries() {
        assertThat(DayWindow.of(LocalDate.of(2026, 10, 31), KOLKATA).until())
                .isEqualTo(Instant.parse("2026-10-31T18:29:59Z"));
        assertThat(DayWindow.of(LocalDate.of(2027, 1, 1), KOLKATA).since())
                .isEqualTo(Instant.parse("2026-12-31T18:30:00Z"));
    }

    @Test
    void consecutiveDaysDontOverlapOrLeaveAGap() {
        DayWindow monday = DayWindow.of(LocalDate.of(2026, 10, 12), LOS_ANGELES);
        DayWindow tuesday = DayWindow.of(LocalDate.of(2026, 10, 13), LOS_ANGELES);

        assertThat(tuesday.since()).isEqualTo(monday.until().plusSeconds(1));
    }

    @Test
    void todayInTheUsersZone() {
        Instant now = Instant.parse("2026-10-15T20:00:00Z");

        assertThat(DayWindow.today(now, ZoneOffset.UTC)).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(DayWindow.today(now, KOLKATA)).isEqualTo(LocalDate.of(2026, 10, 16)); // 01:30 local
        assertThat(DayWindow.today(Instant.parse("2026-10-16T03:00:00Z"), LOS_ANGELES))
                .isEqualTo(LocalDate.of(2026, 10, 15)); // 20:00 local
    }
}
