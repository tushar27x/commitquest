package com.tushar27x.insightshub.sync;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * One calendar day in a user's timezone, as the instants GitHub's {@code since}/{@code until} take.
 * {@code until} is the day's last second, so consecutive days neither overlap nor leave a gap even if
 * GitHub treats {@code until} as inclusive. A DST day is 23 or 25 hours long.
 */
public record DayWindow(LocalDate date, Instant since, Instant until) {

    public static DayWindow of(LocalDate date, ZoneId zone) {
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant nextStart = date.plusDays(1).atStartOfDay(zone).toInstant();
        return new DayWindow(date, start, nextStart.minusSeconds(1));
    }

    /** The user's local date at {@code now}. */
    public static LocalDate today(Instant now, ZoneId zone) {
        return LocalDate.ofInstant(now, zone);
    }
}
