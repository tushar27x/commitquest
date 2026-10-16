package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import com.tushar27x.insightshub.repository.DailyContributionsRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityWriterTest {

    private static final long ID = 583231L;
    private static final LocalDate TODAY = LocalDate.parse("2026-10-14");

    private final DailyContributionsRepository repository = mock(DailyContributionsRepository.class);
    private final ActivityWriter writer = new ActivityWriter(repository);

    /** A year of days, oldest first, like the calendar GitHub returns. */
    private final List<ContributionDay> year = IntStream.rangeClosed(0, 365)
            .mapToObj(i -> new ContributionDay(TODAY.minusDays(365 - i), i % 3))
            .toList();

    @Test
    void firstSyncBackfillsTheWholeCalendar() {
        when(repository.latestDate(ID)).thenReturn(Optional.empty());

        assertThat(writer.writeCalendar(ID, year)).isEqualTo(366);
        verify(repository).upsertCalendarCounts(ID, year);
    }

    @Test
    void laterSyncRewritesOnlyTheOverlapWeekAndNewDays() {
        when(repository.latestDate(ID)).thenReturn(Optional.of(TODAY.minusDays(1)));

        assertThat(writer.writeCalendar(ID, year)).isEqualTo(9); // Oct 6 (yesterday − 7) .. Oct 14
        verify(repository).upsertCalendarCounts(ID, year.subList(year.size() - 9, year.size()));
    }

    @Test
    void gapSinceTheLastSyncIsFilled() {
        LocalDate lastSync = LocalDate.parse("2026-08-31"); // six weeks without a sync, across a month boundary

        List<ContributionDay> window = ActivityWriter.recentWindow(year, Optional.of(lastSync));

        assertThat(window.getFirst().date()).isEqualTo(LocalDate.parse("2026-08-24"));
        assertThat(window.getLast().date()).isEqualTo(TODAY);
        assertThat(window).hasSize(52);
    }

    @Test
    void storedDaysNewerThanTheCalendarStillRewriteTheOverlap() {
        // e.g. a stored day ahead of GitHub's calendar; nothing new, but the last week is still refreshed
        List<ContributionDay> window = ActivityWriter.recentWindow(year, Optional.of(TODAY.plusDays(1)));

        assertThat(window).extracting(ContributionDay::date).first().isEqualTo(TODAY.minusDays(6));
        assertThat(window).hasSize(7);
    }

    @Test
    void emptyCalendarWritesNothing() {
        when(repository.latestDate(ID)).thenReturn(Optional.empty());

        assertThat(writer.writeCalendar(ID, List.of())).isZero();
        verify(repository).upsertCalendarCounts(ID, List.of());
    }
}
