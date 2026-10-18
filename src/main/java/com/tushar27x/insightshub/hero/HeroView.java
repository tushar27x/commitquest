package com.tushar27x.insightshub.hero;

import com.tushar27x.insightshub.entity.Archetype;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What {@code GET /api/user/hero} derives from the stored hero (docs/api/contract.md), as pure functions
 * like {@link HeroRules}: no clock, no database.
 */
public final class HeroView {

    /** After this local time with no activity, the hero is hungry. */
    public static final LocalTime HUNGRY_FROM = LocalTime.of(18, 0);

    private HeroView() {
    }

    /** 3 hearts HAPPY, 2 OKAY, 1 WEAK: what the sprite shows under an overlay. */
    public static Mood baseMood(int lives) {
        return switch (lives) {
            case 3 -> Mood.HAPPY;
            case 2 -> Mood.OKAY;
            default -> Mood.WEAK;
        };
    }

    public static Tier tier(int level) {
        if (level >= 15) {
            return Tier.CITADEL;
        }
        if (level >= 10) {
            return Tier.KEEP;
        }
        return level >= 5 ? Tier.VILLAGE : Tier.CAMPFIRE;
    }

    /**
     * The mood now, strongest first:
     * <ol>
     *   <li>a one-off reaction to the last settled day, shown until the next evaluation: {@code FAINTED} when its
     *       hearts ran out (a {@code LEVEL_DOWN} or {@code FAINTED} event: the overlay reads "LV n → n−1"),
     *       {@code LEVEL_UP} after a level-up;</li>
     *   <li>{@code SLEEPING} on a rest day with no activity yet;</li>
     *   <li>{@code HUNGRY} from 18:00 local with no activity yet;</li>
     *   <li>the base mood.</li>
     * </ol>
     *
     * @param lastSettledEvents the event types of the day {@code hero_state.last_evaluated_on}
     */
    public static Mood mood(int lives, Set<HeroEventType> lastSettledEvents, boolean restToday, boolean activeToday,
                            LocalTime localTime) {
        if (lastSettledEvents.contains(HeroEventType.FAINTED) || lastSettledEvents.contains(HeroEventType.LEVEL_DOWN)) {
            return Mood.FAINTED;
        }
        if (lastSettledEvents.contains(HeroEventType.LEVEL_UP)) {
            return Mood.LEVEL_UP;
        }
        if (!activeToday && restToday) {
            return Mood.SLEEPING;
        }
        if (!activeToday && !localTime.isBefore(HUNGRY_FROM)) {
            return Mood.HUNGRY;
        }
        return baseMood(lives);
    }

    /**
     * Monday to Sunday of {@code today}'s week. Past days are {@code active}, or {@code rest} on an inactive rest
     * day, else {@code missed}. Today is {@code today}, or {@code rest} on a rest day. Later days are
     * {@code future}, or {@code rest}.
     *
     * @param active  whether a day had any activity (calendar or branch scan)
     * @param restDay whether a day is a rest day under the rest days that apply on it
     */
    public static List<WeekDay> week(LocalDate today, Predicate<LocalDate> active, Predicate<LocalDate> restDay) {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        List<WeekDay> week = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            WeekStatus status;
            if (date.isBefore(today)) {
                status = active.test(date) ? WeekStatus.ACTIVE : restDay.test(date) ? WeekStatus.REST : WeekStatus.MISSED;
            } else if (date.equals(today)) {
                status = restDay.test(date) ? WeekStatus.REST : WeekStatus.TODAY;
            } else {
                status = restDay.test(date) ? WeekStatus.REST : WeekStatus.FUTURE;
            }
            week.add(new WeekDay(date, status));
        }
        return week;
    }

    /**
     * The class block. Qualified: every class scoring at least {@code qualifyScore}, plus the top-scored class,
     * highest score first (ties in declaration order, like the classifier). A chosen class that dropped below the
     * threshold is kept as {@code current}; it's just no longer listed as qualified.
     *
     * @param scores    0–100 per class; a missing class counts as 0
     * @param scored    the top-scored class ({@code insights.archetype})
     * @param chosen    {@code users.chosen_archetype}; null follows {@code scored}
     * @param changedAt the last class change; the cooldown runs from it
     */
    public static ClassView classView(Map<Archetype, Integer> scores, Archetype scored, Archetype chosen,
                                      Instant changedAt, Instant now, int qualifyScore, Duration cooldown) {
        List<Archetype> qualified = Arrays.stream(Archetype.values())
                .filter(archetype -> archetype == scored || scores.getOrDefault(archetype, 0) >= qualifyScore)
                .sorted(Comparator.comparingInt((Archetype archetype) -> scores.getOrDefault(archetype, 0)).reversed()
                        .thenComparing(Archetype::ordinal))
                .toList();
        Instant canSwitchAt = changedAt == null ? null : changedAt.plus(cooldown);
        if (canSwitchAt != null && !canSwitchAt.isAfter(now)) {
            canSwitchAt = null;
        }
        return new ClassView(chosen, chosen == null ? scored : chosen, scored, qualified, canSwitchAt);
    }

    public record WeekDay(LocalDate date, WeekStatus status) {
    }

    /** @param canSwitchAt null when a switch is allowed now */
    public record ClassView(Archetype chosen, Archetype current, Archetype scored, List<Archetype> qualified,
                            Instant canSwitchAt) {
    }
}
