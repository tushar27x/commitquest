package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import com.tushar27x.insightshub.repository.DailyContributionsRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Writes the contribution calendar into {@code daily_contributions}. A separate bean for the same
 * reason as {@link InsightsWriter}: {@code @Transactional} only applies through the Spring proxy.
 */
@Component
public class ActivityWriter {

    /**
     * Days before the latest stored one that are written again. GitHub can still change a recent
     * day: commits are counted on their author date once they reach the default branch, which can
     * be days later, and its data can lag. A week covers that; older days are taken as final.
     */
    static final int OVERLAP_DAYS = 7;

    private final DailyContributionsRepository repository;

    public ActivityWriter(DailyContributionsRepository repository) {
        this.repository = repository;
    }

    /**
     * Upserts {@code calendar_count} from the calendar. The first sync backfills every day GitHub
     * returned (a year); later syncs only rewrite the days from the overlap window onwards, which
     * also fills any gap since the last sync.
     *
     * @param days the calendar, sorted by date ({@code InsightsEngine.sortedDays})
     * @return how many days were written
     */
    @Transactional
    public int writeCalendar(long userId, List<ContributionDay> days) {
        List<ContributionDay> window = recentWindow(days, repository.latestDate(userId));
        repository.upsertCalendarCounts(userId, window);
        return window.size();
    }

    static List<ContributionDay> recentWindow(List<ContributionDay> days, Optional<LocalDate> latestStored) {
        if (latestStored.isEmpty()) {
            return days;
        }
        LocalDate from = latestStored.get().minusDays(OVERLAP_DAYS);
        return days.stream().filter(day -> !day.date().isBefore(from)).toList();
    }
}
