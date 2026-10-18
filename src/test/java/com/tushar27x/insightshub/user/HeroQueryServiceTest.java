package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.entity.Insights;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;
import com.tushar27x.insightshub.hero.HeroProperties;
import com.tushar27x.insightshub.hero.HeroState;
import com.tushar27x.insightshub.hero.InMemoryHeroRepository;
import com.tushar27x.insightshub.hero.Mood;
import com.tushar27x.insightshub.hero.RestDays;
import com.tushar27x.insightshub.hero.WeekStatus;
import com.tushar27x.insightshub.repository.DailyContributionsRepository;
import com.tushar27x.insightshub.repository.DailyContributionsRepository.DayCounts;
import com.tushar27x.insightshub.repository.HeroRepository.SettledDay;
import com.tushar27x.insightshub.repository.InsightsRepository;
import com.tushar27x.insightshub.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GET /hero and /hero/events computed from stored rows. The fixture story (plan §5) goes in as database rows
 * and must come out exactly as {@code docs/api/fixtures/hero.json} and {@code hero-events.json}.
 */
class HeroQueryServiceTest {

    private static final long ID = 583231L;
    /** Friday 16 Oct 2026, 18:20 in Kolkata. */
    private static final Instant FRIDAY_EVENING = Instant.parse("2026-10-16T12:50:00Z");
    private static final LocalDate MON = LocalDate.of(2026, 10, 12);

    private final JsonMapper json = JsonMapper.builder().build();
    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final InsightsRepository insightsRepository = mock(InsightsRepository.class);
    private final DailyContributionsRepository activity = mock(DailyContributionsRepository.class);
    private final InMemoryHeroRepository heroes = new InMemoryHeroRepository();
    private final Map<LocalDate, DayCounts> stored = new TreeMap<>();
    private Users user;

    @BeforeEach
    void fixtureStory() {
        user = new Users(ID, "tushar27x");
        user.setTimezone("Asia/Kolkata");
        user.setRestDays((short) RestDays.toMask(Set.of(DayOfWeek.SUNDAY)));
        Insights insights = new Insights(user);
        insights.setArchetype(Archetype.MONK);
        insights.setArchetypeScores(new HashMap<>(Map.of("PALADIN", 44, "BARBARIAN", 44, "WIZARD", 33,
                "ARTIFICER", 33, "CLERIC", 0, "BARD", 10, "MONK", 52, "ROGUE", 47)));
        when(usersRepository.findById(ID)).thenReturn(Optional.of(user));
        when(insightsRepository.findById(ID)).thenReturn(Optional.of(insights));
        when(activity.findCounts(anyLong(), any(), any())).thenAnswer(invocation ->
                new TreeMap<>(stored).subMap(invocation.getArgument(1), true, invocation.getArgument(2), true));

        // Mon and Tue active, Wed missed (a heart lost), Thu active: level 7, 140/350 XP, 2 hearts, streak 1
        stored.put(MON, new DayCounts(5, 0));
        stored.put(MON.plusDays(1), new DayCounts(2, 0));
        stored.put(MON.plusDays(3), new DayCounts(0, 1));
        heroes.states.put(ID, new HeroState(7, 140, 2, 1, MON.plusDays(3)));
        heroes.insertEvents(ID, List.of(new HeroEvent(HeroEventType.LIFE_LOST, MON.plusDays(2),
                Map.of("lives", 2, "reason", "NO_ACTIVITY"))));
    }

    private HeroQueryService serviceAt(Instant now) {
        return new HeroQueryService(usersRepository, insightsRepository, heroes, activity,
                new HeroProperties(4, 40, Duration.ofDays(7)), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void theFixtureStoryIsExactlyHeroJson() {
        HeroResponse expected = json.readValue(Fixtures.read("hero.json"), HeroResponse.class);

        assertThat(serviceAt(FRIDAY_EVENING).hero(ID)).contains(expected);
    }

    @Test
    void beforeSixTheSameHeroIsOkayNotHungry() {
        HeroResponse hero = serviceAt(Instant.parse("2026-10-16T12:29:00Z")).hero(ID).orElseThrow(); // 17:59 local

        assertThat(hero.mood()).isEqualTo(Mood.OKAY);
        assertThat(hero.baseMood()).isEqualTo(Mood.OKAY);
    }

    @Test
    void aContributionTodayFeedsTheHeroAtOnce() {
        stored.put(MON.plusDays(4), new DayCounts(0, 2)); // a branch commit, after a SAVE

        HeroResponse hero = serviceAt(FRIDAY_EVENING).hero(ID).orElseThrow();

        assertThat(hero.today()).isEqualTo(new HeroResponse.Today(MON.plusDays(4), true, 0, 2,
                Instant.parse("2026-10-16T18:30:00Z")));
        assertThat(hero.mood()).isEqualTo(Mood.OKAY);
    }

    @Test
    void sundayIsSleeping() {
        stored.put(MON.plusDays(4), new DayCounts(1, 0));
        stored.put(MON.plusDays(5), new DayCounts(1, 0));
        heroes.states.put(ID, new HeroState(7, 160, 3, 3, MON.plusDays(5)));

        HeroResponse hero = serviceAt(Instant.parse("2026-10-18T12:50:00Z")).hero(ID).orElseThrow();

        assertThat(hero.mood()).isEqualTo(Mood.SLEEPING);
        assertThat(hero.baseMood()).isEqualTo(Mood.HAPPY);
        assertThat(hero.week()).extracting(HeroResponse.Day::status).containsExactly(WeekStatus.ACTIVE,
                WeekStatus.ACTIVE, WeekStatus.MISSED, WeekStatus.ACTIVE, WeekStatus.ACTIVE, WeekStatus.ACTIVE,
                WeekStatus.REST);
    }

    @Test
    void aLevelUpShowsTheDayAfter() {
        heroes.insertEvents(ID, List.of(new HeroEvent(HeroEventType.LEVEL_UP, MON.plusDays(3), Map.of("from", 6, "to", 7))));

        assertThat(serviceAt(FRIDAY_EVENING).hero(ID).orElseThrow().mood()).isEqualTo(Mood.LEVEL_UP);
    }

    @Test
    void aRestDayChangeShowsNowButAppliesFromTomorrow() {
        // Set on Friday: from Saturday the rest day is Friday, so today keeps the old mask and Sunday is no longer one
        user.setRestDaysPrevious(user.getRestDays());
        user.setRestDays((short) RestDays.toMask(Set.of(DayOfWeek.FRIDAY)));
        user.setRestDaysFrom(MON.plusDays(5));

        HeroResponse hero = serviceAt(FRIDAY_EVENING).hero(ID).orElseThrow();

        assertThat(hero.restDays()).containsExactly(DayOfWeek.FRIDAY);
        assertThat(hero.week().get(4).status()).isEqualTo(WeekStatus.TODAY);
        assertThat(hero.week().get(6).status()).isEqualTo(WeekStatus.FUTURE);
        assertThat(hero.mood()).isEqualTo(Mood.HUNGRY);
    }

    @Test
    void theChosenClassAndItsCooldown() {
        user.setChosenArchetype(Archetype.ROGUE);
        user.setArchetypeChangedAt(Instant.parse("2026-10-14T09:00:00Z"));

        HeroResponse.HeroClass heroClass = serviceAt(FRIDAY_EVENING).hero(ID).orElseThrow().heroClass();

        assertThat(heroClass).isEqualTo(new HeroResponse.HeroClass(Archetype.ROGUE, Archetype.ROGUE, Archetype.MONK,
                List.of(Archetype.MONK, Archetype.ROGUE, Archetype.PALADIN, Archetype.BARBARIAN),
                Instant.parse("2026-10-21T09:00:00Z")));
    }

    @Test
    void noHeroOrNoInsightsIsEmpty() {
        heroes.states.clear();
        assertThat(serviceAt(FRIDAY_EVENING).hero(ID)).isEmpty();
        assertThat(serviceAt(FRIDAY_EVENING).events(ID, 30)).isEmpty();

        heroes.states.put(ID, new HeroState(1, 0, 3, 0, MON));
        when(insightsRepository.findById(ID)).thenReturn(Optional.empty());
        assertThat(serviceAt(FRIDAY_EVENING).hero(ID)).isEmpty();
    }

    // --- events ---

    @Test
    void theFixtureTimelineIsExactlyHeroEventsJson() {
        String fixture = Fixtures.read("hero-events.json");
        HeroEventsResponse expected = json.readValue(fixture, HeroEventsResponse.class);
        heroes.events.clear();
        heroes.insertEvents(ID, List.of(new HeroEvent(HeroEventType.RESTED, LocalDate.of(2026, 9, 13), Map.of()))); // outside
        heroes.insertDays(ID, List.of(new SettledDay(LocalDate.of(2026, 9, 15), 10, 9))); // outside
        expected.events().forEach(event -> heroes.insertEvents(ID,
                List.of(new HeroEvent(event.type(), event.onDate(), event.details()))));
        heroes.insertDays(ID, expected.xpDays().stream()
                .map(day -> new SettledDay(day.onDate(), day.xp(), day.streak())).toList());

        assertThat(serviceAt(FRIDAY_EVENING).events(ID, 30)).contains(expected);
    }

    @Test
    void theWindowEndsTodayInTheUsersZone() {
        heroes.insertDays(ID, List.of(new SettledDay(MON.plusDays(2), 0, 0), new SettledDay(MON.plusDays(3), 10, 1)));

        HeroEventsResponse oneDay = serviceAt(FRIDAY_EVENING).events(ID, 1).orElseThrow();

        assertThat(oneDay.xpDays()).containsExactly(new HeroEventsResponse.XpDay(MON.plusDays(3), 10, 1));
        assertThat(oneDay.events()).isEmpty();
    }
}
