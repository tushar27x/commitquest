package com.tushar27x.insightshub.hero;

import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.hero.HeroService.DueHero;
import com.tushar27x.insightshub.repository.DailyContributionsRepository;
import com.tushar27x.insightshub.repository.DailyContributionsRepository.DayCounts;
import com.tushar27x.insightshub.repository.HeroRepository.SettledDay;
import com.tushar27x.insightshub.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static com.tushar27x.insightshub.hero.HeroEventType.LEVEL_DOWN;
import static com.tushar27x.insightshub.hero.HeroEventType.LIFE_GAINED;
import static com.tushar27x.insightshub.hero.HeroEventType.LIFE_LOST;
import static com.tushar27x.insightshub.hero.HeroEventType.RESTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The hourly job's per-user work (B5): seed on first sync, then settle the due days in order, once.
 * The rules themselves are {@link HeroRulesTest}'s; here it's which days, which inputs, and idempotency.
 */
class HeroServiceTest {

    private static final long ID = 583231L;
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final LocalDate OCT_12 = LocalDate.of(2026, 10, 12); // a Monday
    private static final LocalDate OCT_13 = OCT_12.plusDays(1);
    private static final LocalDate OCT_14 = OCT_12.plusDays(2);
    private static final LocalDate OCT_15 = OCT_12.plusDays(3);

    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final DailyContributionsRepository activity = mock(DailyContributionsRepository.class);
    private final InMemoryHeroRepository heroes = new InMemoryHeroRepository();
    private final Map<LocalDate, DayCounts> stored = new TreeMap<>();
    private Users user;

    @BeforeEach
    void setUp() {
        user = new Users(ID, "octocat");
        user.setTimezone("Asia/Kolkata");
        signedUpOn(Instant.parse("2026-09-01T10:00:00Z"));
        when(usersRepository.findById(ID)).thenReturn(Optional.of(user));
        when(activity.findCounts(anyLong(), any(), any())).thenAnswer(invocation -> {
            LocalDate from = invocation.getArgument(1);
            LocalDate to = invocation.getArgument(2);
            return new TreeMap<>(stored).subMap(from, true, to, true);
        });
    }

    private HeroService serviceAt(String instant) {
        return new HeroService(usersRepository, heroes, activity, new HeroWriter(heroes, usersRepository),
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private void signedUpOn(Instant createdAt) {
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
    }

    private void active(LocalDate... dates) {
        for (LocalDate date : dates) {
            stored.put(date, new DayCounts(2, 0));
        }
    }

    // --- seed ---

    @Test
    void firstSyncSeedsXpAndStreakFromTheLast90LocalDays() {
        active(OCT_13, OCT_14, OCT_15, LocalDate.of(2026, 7, 18)); // Jul 18 is the 90th day back from Oct 15
        stored.put(LocalDate.of(2026, 7, 17), new DayCounts(5, 0)); // 91 days back: too old
        stored.put(LocalDate.of(2026, 10, 16), new DayCounts(1, 0)); // today in Kolkata: not settled yet

        // 20:00 UTC on Oct 15 is 01:30 on Oct 16 in Kolkata, so yesterday is Oct 15
        assertThat(serviceAt("2026-10-15T20:00:00Z").seedIfMissing(ID)).isTrue();

        assertThat(heroes.states.get(ID)).isEqualTo(new HeroState(1, 40, 3, 3, OCT_15));
        assertThat(heroes.eventsOf(ID)).isEmpty();
    }

    @Test
    void aBranchCommitCountsAsActiveForTheSeed() {
        stored.put(OCT_15, new DayCounts(0, 3));

        serviceAt("2026-10-16T12:00:00Z").seedIfMissing(ID);

        assertThat(heroes.states.get(ID).xp()).isEqualTo(10);
    }

    @Test
    void anExistingHeroIsNeverReseeded() {
        HeroState existing = new HeroState(7, 140, 2, 1, OCT_14);
        heroes.states.put(ID, existing);
        active(OCT_15);

        assertThat(serviceAt("2026-10-16T12:00:00Z").seedIfMissing(ID)).isFalse();
        assertThat(heroes.states.get(ID)).isEqualTo(existing);
    }

    // --- evaluate ---

    @Test
    void nothingIsDueBeforeSixInTheMorningLocal() {
        heroes.states.put(ID, new HeroState(1, 0, 3, 0, OCT_14));
        active(OCT_15);

        // 00:29 UTC on Oct 16 is 05:59 in Kolkata
        assertThat(serviceAt("2026-10-16T00:29:00Z").evaluate(ID)).isZero();
        assertThat(heroes.states.get(ID).lastEvaluatedOn()).isEqualTo(OCT_14);

        assertThat(serviceAt("2026-10-16T00:30:00Z").evaluate(ID)).isEqualTo(1);
        assertThat(heroes.states.get(ID)).isEqualTo(new HeroState(1, 10, 3, 1, OCT_15));
    }

    @Test
    void aThreeDayOutageIsCaughtUpInOrder() {
        heroes.states.put(ID, new HeroState(2, 95, 1, 5, OCT_12));
        active(OCT_13); // Tue active, Wed and Thu missed

        assertThat(serviceAt("2026-10-16T09:00:00Z").evaluate(ID)).isEqualTo(3);

        // Oct 13: +10 XP → level 3 (100 XP), streak 6 = 2 × 3 → +1 heart. Oct 14: lose it. Oct 15: last heart → level 2
        assertThat(heroes.states.get(ID)).isEqualTo(new HeroState(2, 0, 3, 0, OCT_15));
        assertThat(heroes.eventsOf(ID)).extracting(HeroEvent::type, HeroEvent::onDate).containsExactly(
                org.assertj.core.groups.Tuple.tuple(HeroEventType.LEVEL_UP, OCT_13),
                org.assertj.core.groups.Tuple.tuple(LIFE_GAINED, OCT_13),
                org.assertj.core.groups.Tuple.tuple(LIFE_LOST, OCT_14),
                org.assertj.core.groups.Tuple.tuple(LIFE_LOST, OCT_15),
                org.assertj.core.groups.Tuple.tuple(LEVEL_DOWN, OCT_15));
        assertThat(heroes.daysOf(ID)).containsExactly(
                new SettledDay(OCT_13, 10, 6), new SettledDay(OCT_14, 0, 0), new SettledDay(OCT_15, 0, 0));
    }

    @Test
    void runningTwiceSettlesEachDayOnce() {
        heroes.states.put(ID, new HeroState(1, 0, 3, 0, OCT_12));
        HeroService service = serviceAt("2026-10-16T09:00:00Z");

        assertThat(service.evaluate(ID)).isEqualTo(3);
        HeroState after = heroes.states.get(ID);
        int events = heroes.eventsOf(ID).size();

        assertThat(service.evaluate(ID)).isZero();
        assertThat(heroes.states.get(ID)).isEqualTo(after);
        assertThat(heroes.eventsOf(ID)).hasSize(events);
        assertThat(heroes.daysOf(ID)).hasSize(3);
    }

    @Test
    void aRunThatLostTheRaceWritesNothing() {
        heroes.states.put(ID, new HeroState(1, 0, 3, 0, OCT_14));
        HeroRules.CatchUp stale = HeroRules.evaluateDays(new HeroState(1, 0, 3, 0, OCT_13),
                List.of(new HeroDay(OCT_14, 0, 0, false, false), new HeroDay(OCT_15, 0, 0, false, false)));

        // Another run already moved the hero to Oct 14: this one read Oct 13
        assertThat(new HeroWriter(heroes, usersRepository).apply(ID, OCT_13, stale, Instant.now())).isFalse();

        assertThat(heroes.states.get(ID).lastEvaluatedOn()).isEqualTo(OCT_14);
        assertThat(heroes.eventsOf(ID)).isEmpty();
        assertThat(heroes.daysOf(ID)).isEmpty();
    }

    @Test
    void aNewUserWithoutAHeroIsSkipped() {
        assertThat(serviceAt("2026-10-16T09:00:00Z").evaluate(ID)).isZero();
        assertThat(heroes.states).isEmpty();
    }

    @Test
    void restDaysUseTheMaskThatAppliedOnEachDay() {
        // Wednesday was the rest day until Oct 14; from Oct 15 it's Thursday
        user.setRestDays((short) RestDays.toMask(Set.of(DayOfWeek.THURSDAY)));
        user.setRestDaysPrevious((short) RestDays.toMask(Set.of(DayOfWeek.WEDNESDAY)));
        user.setRestDaysFrom(OCT_15);
        heroes.states.put(ID, new HeroState(1, 0, 3, 0, OCT_13));

        serviceAt("2026-10-16T09:00:00Z").evaluate(ID); // Wed Oct 14 and Thu Oct 15, both inactive

        assertThat(heroes.eventsOf(ID)).extracting(HeroEvent::type).containsExactly(RESTED, RESTED);
        assertThat(heroes.states.get(ID).lives()).isEqualTo(3);
    }

    @Test
    void aChangeMadeTodayDoesNotApplyToTheDaysBeingSettled() {
        user.setRestDays((short) RestDays.toMask(Set.of(DayOfWeek.THURSDAY))); // set on Oct 16, applies from Oct 17
        user.setRestDaysPrevious((short) 0);
        user.setRestDaysFrom(LocalDate.of(2026, 10, 17));
        heroes.states.put(ID, new HeroState(1, 0, 3, 0, OCT_14));

        serviceAt("2026-10-16T09:00:00Z").evaluate(ID); // Thu Oct 15, inactive

        assertThat(heroes.eventsOf(ID)).extracting(HeroEvent::type).containsExactly(LIFE_LOST);
    }

    @Test
    void inactiveDaysUpToTheSignUpDayCostNothing() {
        signedUpOn(Instant.parse("2026-10-14T20:00:00Z")); // Oct 15, 01:30 in Kolkata
        heroes.states.put(ID, new HeroState(1, 0, 3, 0, OCT_13)); // seeded in UTC before the zone was set

        serviceAt("2026-10-17T03:00:00Z").evaluate(ID); // settles Oct 14, 15 (sign-up) and 16

        assertThat(heroes.eventsOf(ID)).extracting(HeroEvent::type, HeroEvent::onDate)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(LIFE_LOST, LocalDate.of(2026, 10, 16)));
    }

    @Test
    void daysAreLocalDaysInTheUsersZone() {
        user.setTimezone("America/Los_Angeles");
        heroes.states.put(ID, new HeroState(1, 0, 3, 0, OCT_14));
        active(OCT_15);

        // 09:00 UTC on Oct 16 is 02:00 in Los Angeles: Oct 15 isn't due there yet
        assertThat(serviceAt("2026-10-16T09:00:00Z").evaluate(ID)).isZero();
        assertThat(serviceAt("2026-10-16T13:00:00Z").evaluate(ID)).isEqualTo(1);
    }

    @Test
    void anUnknownStoredZoneFallsBackToUtc() {
        assertThat(HeroService.zoneOf("Mars/Olympus_Mons")).isEqualTo(ZoneOffset.UTC);
        assertThat(HeroService.zoneOf(null)).isEqualTo(ZoneOffset.UTC);
        assertThat(HeroService.zoneOf("UTC")).isEqualTo(ZoneOffset.UTC);
        assertThat(HeroService.zoneOf("Asia/Kolkata")).isEqualTo(KOLKATA);
    }

    // --- which heroes are due ---

    @Test
    void dueHeroesAreFilteredByEachUsersLocalMorning() {
        Map<Long, String> zones = new HashMap<>(Map.of(1L, "Asia/Kolkata", 2L, "America/Los_Angeles", 3L, "UTC"));
        zones.forEach((id, zone) -> {
            heroes.states.put(id, new HeroState(1, 0, 3, 0, OCT_14));
            heroes.timezones.put(id, zone);
        });
        heroes.states.put(4L, new HeroState(1, 0, 3, 0, OCT_15)); // already settled yesterday
        heroes.timezones.put(4L, "Asia/Kolkata");

        // 03:00 UTC Oct 16: 08:30 in Kolkata, 20:00 Oct 15 in Los Angeles, 03:00 in UTC
        List<DueHero> due = serviceAt("2026-10-16T03:00:00Z").dueHeroes();

        assertThat(due).containsExactly(new DueHero(1L, OCT_15));
    }

    @Test
    void aLongOutageReportsTheOldestDueDay() {
        heroes.states.put(1L, new HeroState(1, 0, 3, 0, LocalDate.of(2026, 10, 1)));
        heroes.timezones.put(1L, "UTC");

        assertThat(serviceAt("2026-10-16T09:00:00Z").dueHeroes())
                .containsExactly(new DueHero(1L, LocalDate.of(2026, 10, 2)));
    }

    @Test
    void losAngelesAcrossTheDstChange() {
        // PDT ends on Sunday Nov 1, 2026: Nov 1 is 25 hours long, and 06:00 on Nov 2 is 14:00 UTC
        user.setTimezone("America/Los_Angeles");
        heroes.states.put(ID, new HeroState(1, 0, 3, 0, LocalDate.of(2026, 10, 31)));
        active(LocalDate.of(2026, 11, 1));

        assertThat(serviceAt("2026-11-02T13:59:00Z").evaluate(ID)).isZero();
        assertThat(serviceAt("2026-11-02T14:00:00Z").evaluate(ID)).isEqualTo(1);
        assertThat(heroes.states.get(ID).lastEvaluatedOn()).isEqualTo(LocalDate.of(2026, 11, 1));
    }
}
