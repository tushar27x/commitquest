package com.tushar27x.insightshub.hero;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Rest days as stored in {@code users.rest_days}: a 7-bit mask, Monday = bit 0 … Sunday = bit 6, at most
 * 2 bits set (a CHECK in V5). A change applies from the next local day: the user row keeps the previous
 * mask and the date the new one applies from, so catching up on older days uses the right value.
 */
public final class RestDays {

    public static final int MAX = 2;

    private RestDays() {
    }

    public static int toMask(Collection<DayOfWeek> days) {
        int mask = 0;
        for (DayOfWeek day : days) {
            mask |= bit(day);
        }
        return validate(mask);
    }

    /** Monday first. */
    public static List<DayOfWeek> toDays(int mask) {
        validate(mask);
        List<DayOfWeek> days = new ArrayList<>(MAX);
        for (DayOfWeek day : DayOfWeek.values()) {
            if ((mask & bit(day)) != 0) {
                days.add(day);
            }
        }
        return days;
    }

    public static boolean isRestDay(int mask, LocalDate date) {
        return (mask & bit(date.getDayOfWeek())) != 0;
    }

    /**
     * The mask that applied on {@code date}.
     *
     * @param from the first day {@code current} applies; null if the rest days were never changed
     */
    public static int maskOn(LocalDate date, int current, int previous, LocalDate from) {
        return from == null || !date.isBefore(from) ? current : previous;
    }

    private static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }

    private static int validate(int mask) {
        if (mask < 0 || mask > 127) {
            throw new IllegalArgumentException("Rest-day mask must be in [0, 127], was " + mask);
        }
        if (Integer.bitCount(mask) > MAX) {
            throw new IllegalArgumentException("At most " + MAX + " rest days");
        }
        return mask;
    }
}
