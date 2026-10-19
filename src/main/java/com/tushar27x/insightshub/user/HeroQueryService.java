package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.entity.Insights;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;
import com.tushar27x.insightshub.hero.HeroProperties;
import com.tushar27x.insightshub.hero.HeroRules;
import com.tushar27x.insightshub.hero.HeroService;
import com.tushar27x.insightshub.hero.HeroState;
import com.tushar27x.insightshub.hero.HeroView;
import com.tushar27x.insightshub.hero.HeroView.ClassView;
import com.tushar27x.insightshub.hero.Mood;
import com.tushar27x.insightshub.hero.RestDays;
import com.tushar27x.insightshub.repository.DailyContributionsRepository;
import com.tushar27x.insightshub.repository.DailyContributionsRepository.DayCounts;
import com.tushar27x.insightshub.repository.HeroRepository;
import com.tushar27x.insightshub.repository.InsightsRepository;
import com.tushar27x.insightshub.repository.UsersRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The read side of the hero: the stored state plus what's derived on each request (mood, tier, today, the
 * week strip, the class block). Empty until the first sync has created the hero and its insights.
 */
@Service
public class HeroQueryService {

    private final UsersRepository usersRepository;
    private final InsightsRepository insightsRepository;
    private final HeroRepository heroes;
    private final DailyContributionsRepository activity;
    private final HeroProperties properties;
    private final Clock clock;

    public HeroQueryService(UsersRepository usersRepository, InsightsRepository insightsRepository,
                            HeroRepository heroes, DailyContributionsRepository activity, HeroProperties properties,
                            Clock clock) {
        this.usersRepository = usersRepository;
        this.insightsRepository = insightsRepository;
        this.heroes = heroes;
        this.activity = activity;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<HeroResponse> hero(long userId) {
        Optional<HeroState> stored = heroes.findState(userId);
        Optional<Insights> insights = insightsRepository.findById(userId);
        if (stored.isEmpty() || insights.isEmpty()) {
            return Optional.empty();
        }
        HeroState state = stored.get();
        Users user = insights.get().getUser();
        ZoneId zone = HeroService.zoneOf(user.getTimezone());
        Instant now = clock.instant();
        ZonedDateTime local = now.atZone(zone);
        LocalDate today = local.toLocalDate();

        Map<LocalDate, DayCounts> counts = activity.findCounts(userId, today.with(DayOfWeek.MONDAY), today);
        DayCounts todayCounts = counts.getOrDefault(today, DayCounts.NONE);
        Predicate<LocalDate> restDay = date -> RestDays.isRestDay(
                RestDays.maskOn(date, user.getRestDays(), user.getRestDaysPrevious(), user.getRestDaysFrom()), date);
        Predicate<LocalDate> active = date -> counts.getOrDefault(date, DayCounts.NONE).active();

        Set<HeroEventType> lastSettled = EnumSet.noneOf(HeroEventType.class);
        heroes.findEvents(userId, state.lastEvaluatedOn(), state.lastEvaluatedOn())
                .forEach(event -> lastSettled.add(event.type()));
        Mood mood = HeroView.mood(state.lives(), lastSettled, restDay.test(today), todayCounts.active(),
                LocalTime.from(local));

        ClassView classView = HeroView.classView(scores(insights.get()), insights.get().getArchetype(),
                user.getChosenArchetype(), user.getArchetypeChangedAt(), now, properties.qualifyScore(),
                properties.classCooldown());

        return Optional.of(new HeroResponse(
                state.level(),
                state.xp(),
                HeroRules.xpForNext(state.level()),
                state.lives(),
                HeroRules.MAX_LIVES,
                state.streak(),
                HeroRules.STREAK_FOR_LIFE,
                mood,
                HeroView.baseMood(state.lives()),
                HeroView.tier(state.level()),
                new HeroResponse.Today(today, todayCounts.active(), todayCounts.calendarCount(),
                        todayCounts.branchCommitCount(), today.plusDays(1).atStartOfDay(zone).toInstant()),
                HeroView.week(today, active, restDay).stream()
                        .map(day -> new HeroResponse.Day(day.date(), day.status()))
                        .toList(),
                user.getTimezone(),
                RestDays.toDays(user.getRestDays()),
                new HeroResponse.HeroClass(classView.chosen(), classView.current(), classView.scored(),
                        classView.qualified(), classView.canSwitchAt()),
                state.lastEvaluatedOn()));
    }

    /**
     * The last {@code days} local days up to today: events, and one {@code xp_days} row per settled day.
     * Empty if the user has no hero yet.
     */
    @Transactional(readOnly = true)
    public Optional<HeroEventsResponse> events(long userId, int days) {
        if (heroes.findState(userId).isEmpty()) {
            return Optional.empty();
        }
        Users user = usersRepository.findById(userId).orElseThrow();
        LocalDate today = LocalDate.ofInstant(clock.instant(), HeroService.zoneOf(user.getTimezone()));
        LocalDate from = today.minusDays(days);

        List<HeroEventsResponse.Event> events = heroes.findEvents(userId, from, today).stream()
                .map(HeroQueryService::event)
                .toList();
        List<HeroEventsResponse.XpDay> xpDays = heroes.findDays(userId, from, today).stream()
                .map(day -> new HeroEventsResponse.XpDay(day.onDate(), day.xpGained(), day.streak()))
                .toList();
        return Optional.of(new HeroEventsResponse(events, xpDays));
    }

    private static HeroEventsResponse.Event event(HeroEvent event) {
        return new HeroEventsResponse.Event(event.type(), event.onDate(), event.details());
    }

    /** {@code insights.archetype_scores} by class; names that aren't a class any more are ignored. */
    static Map<Archetype, Integer> scores(Insights insights) {
        Map<Archetype, Integer> scores = new EnumMap<>(Archetype.class);
        insights.getArchetypeScores().forEach((name, score) -> {
            for (Archetype archetype : Archetype.values()) {
                if (archetype.name().equals(name) && score != null) {
                    scores.put(archetype, score);
                }
            }
        });
        return scores;
    }
}
