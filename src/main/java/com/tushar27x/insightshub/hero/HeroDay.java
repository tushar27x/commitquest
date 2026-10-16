package com.tushar27x.insightshub.hero;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One local day to settle, as the hero job reads it from {@code daily_contributions} and the user's settings.
 *
 * @param restDay   a rest day under the rest days that applied on {@code date} ({@link RestDays#maskOn})
 * @param signUpDay the day the user signed up: only days after it can cost a heart (§2 "First login")
 */
public record HeroDay(LocalDate date, int calendarCount, int branchCommitCount, boolean restDay, boolean signUpDay) {

    public HeroDay {
        Objects.requireNonNull(date, "date");
        if (calendarCount < 0 || branchCommitCount < 0) {
            throw new IllegalArgumentException("Counts can't be negative");
        }
    }

    /** Fed: any contribution on the calendar or any commit on a branch. */
    public boolean active() {
        return calendarCount > 0 || branchCommitCount > 0;
    }
}
