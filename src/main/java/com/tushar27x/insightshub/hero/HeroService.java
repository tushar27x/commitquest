package com.tushar27x.insightshub.hero;

import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.hero.HeroRules.CatchUp;
import com.tushar27x.insightshub.repository.DailyContributionsRepository;
import com.tushar27x.insightshub.repository.DailyContributionsRepository.DayCounts;
import com.tushar27x.insightshub.repository.HeroRepository;
import com.tushar27x.insightshub.repository.HeroRepository.Candidate;
import com.tushar27x.insightshub.repository.UsersRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Moves the stored hero with {@link HeroRules}: seeds it on the first sync and settles the days that are due.
 * Reads happen outside a transaction (they're short); each write is one transaction in {@link HeroWriter}.
 * No GitHub calls here: {@link HeroJob} refreshes the activity first.
 */
@Slf4j
@Service
public class HeroService {

    private final UsersRepository usersRepository;
    private final HeroRepository heroes;
    private final DailyContributionsRepository activity;
    private final HeroWriter writer;
    private final Clock clock;

    public HeroService(UsersRepository usersRepository, HeroRepository heroes,
                       DailyContributionsRepository activity, HeroWriter writer, Clock clock) {
        this.usersRepository = usersRepository;
        this.heroes = heroes;
        this.activity = activity;
        this.writer = writer;
        this.clock = clock;
    }

    /**
     * First sync: creates the hero from the active days in the {@link HeroRules#SEED_DAYS} local days up to
     * yesterday (§2 "First login"). An existing hero is never touched.
     *
     * @return true if a hero was created
     */
    public boolean seedIfMissing(long userId) {
        if (heroes.findState(userId).isPresent()) {
            return false;
        }
        Users user = load(userId);
        Instant now = clock.instant();
        LocalDate yesterday = LocalDate.ofInstant(now, zoneOf(user.getTimezone())).minusDays(1);
        List<LocalDate> activeDates = activity.findCounts(userId, yesterday.minusDays(HeroRules.SEED_DAYS - 1L), yesterday)
                .entrySet().stream()
                .filter(day -> day.getValue().active())
                .map(Map.Entry::getKey)
                .toList();
        HeroState state = HeroRules.seed(activeDates, yesterday);
        boolean created = writer.seed(userId, state, now);
        if (created) {
            log.info("Hero for '{}' created: level {}, {} XP, streak {}, from {} active days",
                    user.getLogin(), state.level(), state.xp(), state.streak(), activeDates.size());
        }
        return created;
    }

    /**
     * Settles every due day of this user's hero, oldest first, in one transaction.
     *
     * @return how many days were settled: 0 if none were due, the user has no hero yet, or another run got there first
     */
    public int evaluate(long userId) {
        Optional<HeroState> stored = heroes.findState(userId);
        if (stored.isEmpty()) {
            return 0;
        }
        HeroState state = stored.get();
        Users user = load(userId);
        ZoneId zone = zoneOf(user.getTimezone());
        Instant now = clock.instant();
        List<LocalDate> due = HeroRules.dueDays(state.lastEvaluatedOn(), now, zone);
        if (due.isEmpty()) {
            return 0;
        }

        Map<LocalDate, DayCounts> counts = activity.findCounts(userId, due.getFirst(), due.getLast());
        CatchUp catchUp = HeroRules.evaluateDays(state, heroDays(due, counts, user, zone));
        if (!writer.apply(userId, state.lastEvaluatedOn(), catchUp, now)) {
            log.info("Hero for '{}': {} to {} already settled by another run", user.getLogin(), due.getFirst(), due.getLast());
            return 0;
        }
        HeroState after = catchUp.state();
        log.info("Hero for '{}' settled {} day(s) to {}: level {}, {} XP, {} lives, streak {}; events {}",
                user.getLogin(), catchUp.days().size(), after.lastEvaluatedOn(), after.level(), after.xp(),
                after.lives(), after.streak(), catchUp.events().stream().map(HeroEvent::type).toList());
        return catchUp.days().size();
    }

    /**
     * The heroes with at least one day due now, in their own zones. The database only pre-filters on the
     * earliest zone (UTC+18), so the result is exact for every zone.
     */
    public List<DueHero> dueHeroes() {
        Instant now = clock.instant();
        LocalDate latestDueAnywhere = LocalDate.ofInstant(now.minus(HeroRules.GRACE), ZoneOffset.MAX).minusDays(1);
        List<DueHero> due = new ArrayList<>();
        for (Candidate candidate : heroes.findCandidates(latestDueAnywhere)) {
            List<LocalDate> days = HeroRules.dueDays(candidate.lastEvaluatedOn(), now, zoneOf(candidate.timezone()));
            if (!days.isEmpty()) {
                due.add(new DueHero(candidate.userId(), days.getFirst()));
            }
        }
        return due;
    }

    /**
     * The rules' input for each date: activity from {@code daily_contributions} (no row = nothing seen),
     * the rest days that applied on that date, and whether it's on or before the sign-up day (those can't
     * cost a heart; "before" happens when a zone change moves the local dates back).
     */
    static List<HeroDay> heroDays(List<LocalDate> dates, Map<LocalDate, DayCounts> counts, Users user, ZoneId zone) {
        LocalDate signUp = user.getCreatedAt() == null ? null : LocalDate.ofInstant(user.getCreatedAt(), zone);
        return dates.stream().map(date -> {
            DayCounts day = counts.getOrDefault(date, DayCounts.NONE);
            int mask = RestDays.maskOn(date, user.getRestDays(), user.getRestDaysPrevious(), user.getRestDaysFrom());
            return new HeroDay(date, day.calendarCount(), day.branchCommitCount(), RestDays.isRestDay(mask, date),
                    signUp != null && !date.isAfter(signUp));
        }).toList();
    }

    /**
     * The user's zone. {@code PUT /preferences} only stores valid IANA ids, but a bad value must not break the
     * job for everyone, so it falls back to UTC. Normalized, so {@code "UTC"} is {@link ZoneOffset#UTC}.
     */
    public static ZoneId zoneOf(String timezone) {
        if (timezone == null) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone).normalized();
        } catch (DateTimeException e) {
            log.warn("Unknown stored time zone '{}'; using UTC", timezone);
            return ZoneOffset.UTC;
        }
    }

    private Users load(long userId) {
        return usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user " + userId));
    }

    /** @param firstDue the oldest day to settle: the activity refresh starts there */
    public record DueHero(long userId, LocalDate firstDue) {
    }
}
