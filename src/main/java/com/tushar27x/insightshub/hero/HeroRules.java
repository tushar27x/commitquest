package com.tushar27x.insightshub.hero;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The game rules of docs/v2-hero-plan.md §2, as pure functions: no clock, no database, no Spring.
 * The backend is the source of truth; the hero job (B5) only reads the inputs and stores the results.
 * <table>
 *   <caption>Settling one day</caption>
 *   <tr><th>Day was</th><th>Effect</th></tr>
 *   <tr><td>Active (rest day or not)</td><td>+10 XP, streak +1; every 3rd day of the streak +1 heart (max 3)</td></tr>
 *   <tr><td>Rest day, no activity</td><td>Nothing; the streak pauses. {@code RESTED}</td></tr>
 *   <tr><td>Sign-up day, no activity</td><td>Nothing: only days after sign-up can cost a heart</td></tr>
 *   <tr><td>Any other day, no activity</td><td>−1 heart, streak 0. {@code LIFE_LOST}; at 0 hearts: level −1, XP 0,
 *       hearts back to 3 ({@code LEVEL_DOWN}), or at level 1 {@code FAINTED}</td></tr>
 * </table>
 * Level <i>n</i> → <i>n</i>+1 takes {@code 50 × n} XP, with no cap.
 */
public final class HeroRules {

    public static final int MAX_LIVES = 3;
    public static final int XP_PER_ACTIVE_DAY = 10;
    public static final int XP_PER_LEVEL = 50;
    public static final int STREAK_FOR_LIFE = 3;
    /** First login: XP for the active days in this many days, up to yesterday. */
    public static final int SEED_DAYS = 90;
    /** GitHub's data can lag, so a day is settled this long after it ends (local time). */
    public static final Duration GRACE = Duration.ofHours(6);

    static final String NO_ACTIVITY = "NO_ACTIVITY";

    private HeroRules() {
    }

    /** XP needed to go from {@code level} to the next one. */
    public static int xpForNext(int level) {
        return Math.multiplyExact(XP_PER_LEVEL, level);
    }

    /**
     * Settles one day. Days must come in order: a day already settled changes nothing (so re-runs are
     * harmless), and skipping a day is refused.
     */
    public static DayResult evaluateDay(HeroState state, HeroDay day) {
        LocalDate next = state.lastEvaluatedOn().plusDays(1);
        if (day.date().isBefore(next)) {
            return new DayResult(state, List.of(), 0);
        }
        if (day.date().isAfter(next)) {
            throw new IllegalArgumentException("Next day to settle is " + next + ", got " + day.date());
        }

        int level = state.level();
        int xp = state.xp();
        int lives = state.lives();
        int streak = state.streak();
        List<HeroEvent> events = new ArrayList<>(2);
        int xpGained = 0;

        if (day.active()) {
            xpGained = XP_PER_ACTIVE_DAY;
            xp += xpGained;
            if (xp >= xpForNext(level)) { // +10 can't cross two levels: the smallest step is 50
                xp -= xpForNext(level);
                events.add(new HeroEvent(HeroEventType.LEVEL_UP, day.date(), Map.of("from", level, "to", level + 1)));
                level++;
            }
            streak++;
            if (streak % STREAK_FOR_LIFE == 0 && lives < MAX_LIVES) {
                lives++;
                events.add(new HeroEvent(HeroEventType.LIFE_GAINED, day.date(), Map.of("lives", lives, "streak", streak)));
            }
        } else if (day.restDay()) {
            events.add(new HeroEvent(HeroEventType.RESTED, day.date(), Map.of()));
        } else if (!day.signUpDay()) {
            lives--;
            streak = 0;
            events.add(new HeroEvent(HeroEventType.LIFE_LOST, day.date(), Map.of("lives", lives, "reason", NO_ACTIVITY)));
            if (lives == 0) {
                if (level > 1) {
                    events.add(new HeroEvent(HeroEventType.LEVEL_DOWN, day.date(), Map.of("from", level, "to", level - 1)));
                    level--;
                } else {
                    events.add(new HeroEvent(HeroEventType.FAINTED, day.date(), Map.of()));
                }
                xp = 0;
                lives = MAX_LIVES;
            }
        }
        return new DayResult(new HeroState(level, xp, lives, streak, day.date()), List.copyOf(events), xpGained);
    }

    /** Catch-up: settles the days in order, skipping those already settled. */
    public static CatchUp evaluateDays(HeroState state, List<HeroDay> days) {
        List<DayResult> results = new ArrayList<>();
        List<HeroEvent> events = new ArrayList<>();
        for (HeroDay day : days) {
            if (!day.date().isAfter(state.lastEvaluatedOn())) {
                continue;
            }
            DayResult result = evaluateDay(state, day);
            results.add(result);
            events.addAll(result.events());
            state = result.state();
        }
        return new CatchUp(state, List.copyOf(events), List.copyOf(results));
    }

    /**
     * The days ready to settle at {@code now}: from the day after {@code lastEvaluatedOn} up to the last
     * day that ended at least {@link #GRACE} ago in {@code zone}. Oldest first; empty if none.
     */
    public static List<LocalDate> dueDays(LocalDate lastEvaluatedOn, Instant now, ZoneId zone) {
        LocalDate lastDue = LocalDate.ofInstant(now.minus(GRACE), zone).minusDays(1);
        List<LocalDate> due = new ArrayList<>();
        for (LocalDate date = lastEvaluatedOn.plusDays(1); !date.isAfter(lastDue); date = date.plusDays(1)) {
            due.add(date);
        }
        return due;
    }

    /**
     * The hero on first login: 10 XP per active day in the {@link #SEED_DAYS} days up to {@code lastDay}
     * (usually yesterday), 3 hearts, and the streak of active days ending on {@code lastDay}. No events:
     * the seeded days can't cost hearts, and their level-ups aren't news.
     */
    public static HeroState seed(List<LocalDate> activeDates, LocalDate lastDay) {
        LocalDate first = lastDay.minusDays(SEED_DAYS - 1L);
        Set<LocalDate> active = new HashSet<>();
        for (LocalDate date : activeDates) {
            if (!date.isBefore(first) && !date.isAfter(lastDay)) {
                active.add(date);
            }
        }

        int level = 1;
        int xp = active.size() * XP_PER_ACTIVE_DAY;
        while (xp >= xpForNext(level)) {
            xp -= xpForNext(level);
            level++;
        }
        int streak = 0;
        for (LocalDate date = lastDay; active.contains(date); date = date.minusDays(1)) {
            streak++;
        }
        return new HeroState(level, xp, MAX_LIVES, streak, lastDay);
    }

    /** One settled day: the state after it, its events, and the XP it gave (for {@code xp_days}). */
    public record DayResult(HeroState state, List<HeroEvent> events, int xpGained) {
    }

    /** A catch-up run: the final state, every event in order, and each settled day's result. */
    public record CatchUp(HeroState state, List<HeroEvent> events, List<DayResult> days) {
    }
}
