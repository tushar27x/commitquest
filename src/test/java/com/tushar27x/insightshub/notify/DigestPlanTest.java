package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;
import com.tushar27x.insightshub.notify.DigestPlan.Plan;
import com.tushar27x.insightshub.notify.DigestPlan.UnsentEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DigestPlanTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
    private static final LocalTime EIGHT = LocalTime.of(8, 0);

    private static long nextId = 1;

    private static UnsentEvent event(HeroEventType type, LocalDate on, Map<String, Object> details) {
        return new UnsentEvent(nextId++, new HeroEvent(type, on, details));
    }

    private static UnsentEvent lifeLost(int livesLeft, LocalDate on) {
        return event(HeroEventType.LIFE_LOST, on, Map.of("lives", livesLeft, "reason", "NO_ACTIVITY"));
    }

    @Nested
    class Notable {

        @Test
        void lifeLostIsNotableOnlyAtOneHeartLeft() {
            assertThat(DigestPlan.isNotable(lifeLost(1, TODAY).event())).isTrue();
            assertThat(DigestPlan.isNotable(lifeLost(2, TODAY).event())).isFalse();
            // 0 left comes with LEVEL_DOWN or FAINTED, which carry the news
            assertThat(DigestPlan.isNotable(lifeLost(0, TODAY).event())).isFalse();
        }

        @Test
        void levelChangesAndFaintingAreNotable() {
            assertThat(DigestPlan.isNotable(event(HeroEventType.LEVEL_UP, TODAY, Map.of("from", 2, "to", 3)).event())).isTrue();
            assertThat(DigestPlan.isNotable(event(HeroEventType.LEVEL_DOWN, TODAY, Map.of("from", 3, "to", 2)).event())).isTrue();
            assertThat(DigestPlan.isNotable(event(HeroEventType.FAINTED, TODAY, Map.of()).event())).isTrue();
        }

        @Test
        void theRestIsNot() {
            assertThat(DigestPlan.isNotable(event(HeroEventType.LIFE_GAINED, TODAY, Map.of("lives", 3, "streak", 7)).event())).isFalse();
            assertThat(DigestPlan.isNotable(event(HeroEventType.RESTED, TODAY, Map.of()).event())).isFalse();
            assertThat(DigestPlan.isNotable(event(HeroEventType.CLASS_CHANGED, TODAY,
                    Map.of("from", "ROGUE", "to", "WIZARD")).event())).isFalse();
        }
    }

    @Nested
    class Plans {

        @Test
        void aNotableEventMailsEveryFreshEvent() {
            UnsentEvent rested = event(HeroEventType.RESTED, TODAY.minusDays(3), Map.of());
            UnsentEvent lost = lifeLost(2, TODAY.minusDays(2));
            UnsentEvent lastHeart = lifeLost(1, TODAY.minusDays(1));

            Plan plan = DigestPlan.plan(List.of(rested, lost, lastHeart), TODAY, true);

            assertThat(plan.mail()).containsExactly(rested, lost, lastHeart);
            assertThat(plan.skip()).isEmpty();
            assertThat(plan.sends()).isTrue();
        }

        @Test
        void nothingNotableSkipsEverythingWithoutAnEmail() {
            UnsentEvent rested = event(HeroEventType.RESTED, TODAY.minusDays(1), Map.of());
            UnsentEvent lost = lifeLost(2, TODAY.minusDays(1));

            Plan plan = DigestPlan.plan(List.of(rested, lost), TODAY, true);

            assertThat(plan.mail()).isEmpty();
            assertThat(plan.skip()).containsExactly(rested, lost);
            assertThat(plan.sends()).isFalse();
        }

        @Test
        void eventsOlderThanAWeekAreSkippedNotMailed() {
            UnsentEvent stale = event(HeroEventType.LEVEL_UP, TODAY.minusDays(8), Map.of("from", 1, "to", 2));
            UnsentEvent edge = lifeLost(1, TODAY.minusDays(7));

            Plan plan = DigestPlan.plan(List.of(stale, edge), TODAY, true);

            assertThat(plan.mail()).containsExactly(edge);
            assertThat(plan.skip()).containsExactly(stale);
        }

        @Test
        void aStaleNotableEventAloneSendsNothing() {
            UnsentEvent stale = event(HeroEventType.FAINTED, TODAY.minusDays(10), Map.of());
            UnsentEvent fresh = event(HeroEventType.RESTED, TODAY.minusDays(1), Map.of());

            Plan plan = DigestPlan.plan(List.of(stale, fresh), TODAY, true);

            assertThat(plan.sends()).isFalse();
            assertThat(plan.skip()).containsExactly(stale, fresh);
        }

        @Test
        void anIneligibleUserGetsEverythingSkipped() {
            UnsentEvent up = event(HeroEventType.LEVEL_UP, TODAY.minusDays(1), Map.of("from", 1, "to", 2));

            Plan plan = DigestPlan.plan(List.of(up), TODAY, false);

            assertThat(plan.sends()).isFalse();
            assertThat(plan.skip()).containsExactly(up);
        }

        @Test
        void noEventsNoPlan() {
            Plan plan = DigestPlan.plan(List.of(), TODAY, true);

            assertThat(plan.sends()).isFalse();
            assertThat(plan.skip()).isEmpty();
        }
    }

    @Nested
    class Due {

        private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
        private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");

        @Test
        void dueOnceTheLocalSendTimeHasPassed() {
            // 02:29 UTC = 07:59 in Kolkata, 02:30 UTC = 08:00
            assertThat(DigestPlan.isDue(Instant.parse("2026-10-29T02:29:00Z"), KOLKATA, EIGHT, null)).isFalse();
            assertThat(DigestPlan.isDue(Instant.parse("2026-10-29T02:30:00Z"), KOLKATA, EIGHT, null)).isTrue();
            assertThat(DigestPlan.isDue(Instant.parse("2026-10-29T18:29:00Z"), KOLKATA, EIGHT, null)).isTrue(); // 23:59
        }

        @Test
        void notTwiceOnTheSameLocalDay() {
            Instant now = Instant.parse("2026-10-29T16:00:00Z"); // 09:00 in Los Angeles (PDT)
            Instant sentThisMorning = Instant.parse("2026-10-29T15:35:00Z");
            Instant sentYesterday = Instant.parse("2026-10-28T15:35:00Z");
            // 06:00 UTC on the 29th is still the 28th in Los Angeles
            Instant sentLateYesterdayLocal = Instant.parse("2026-10-29T06:00:00Z");

            assertThat(DigestPlan.isDue(now, LOS_ANGELES, EIGHT, sentThisMorning)).isFalse();
            assertThat(DigestPlan.isDue(now, LOS_ANGELES, EIGHT, sentYesterday)).isTrue();
            assertThat(DigestPlan.isDue(now, LOS_ANGELES, EIGHT, sentLateYesterdayLocal)).isTrue();
        }

        @Test
        void followsTheZoneAcrossTheDstChange() {
            // Los Angeles leaves PDT on 1 Nov 2026: 08:00 local is 15:00 UTC before, 16:00 UTC after
            assertThat(DigestPlan.isDue(Instant.parse("2026-10-31T15:00:00Z"), LOS_ANGELES, EIGHT, null)).isTrue();
            assertThat(DigestPlan.isDue(Instant.parse("2026-11-02T15:00:00Z"), LOS_ANGELES, EIGHT, null)).isFalse();
            assertThat(DigestPlan.isDue(Instant.parse("2026-11-02T16:00:00Z"), LOS_ANGELES, EIGHT, null)).isTrue();
        }

        @Test
        void utcAcrossAMonthBoundary() {
            Instant sentOn31st = Instant.parse("2026-10-31T09:00:00Z");
            assertThat(DigestPlan.isDue(Instant.parse("2026-11-01T07:59:00Z"), ZoneOffset.UTC, EIGHT, sentOn31st)).isFalse();
            assertThat(DigestPlan.isDue(Instant.parse("2026-11-01T08:00:00Z"), ZoneOffset.UTC, EIGHT, sentOn31st)).isTrue();
        }

        @Test
        void localTodayIsTheZonesDate() {
            assertThat(DigestPlan.localToday(Instant.parse("2026-10-29T20:00:00Z"), KOLKATA)).isEqualTo(LocalDate.of(2026, 10, 30));
            assertThat(DigestPlan.localToday(Instant.parse("2026-10-29T05:00:00Z"), LOS_ANGELES)).isEqualTo(LocalDate.of(2026, 10, 28));
        }
    }
}
