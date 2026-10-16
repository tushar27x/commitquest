package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.github.GitHubGraphQlClient;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Finds commits the contribution calendar doesn't count: on feature branches, on a non-default
 * deploy branch, in forks. Only the recent days the calendar shows as empty are scanned (on a normal
 * day: today and yesterday), one GraphQL call each, because only {@code > 0} matters to the hero.
 * <p>
 * The calendar's dates are GitHub's own buckets: a commit counts on the day of its own timestamp's
 * timezone (usually the user's). The scan uses the user's timezone.
 */
@Slf4j
@Component
public class BranchScanner {

    private final GitHubGraphQlClient gitHub;
    private final Clock clock;
    private final int days;

    public BranchScanner(GitHubGraphQlClient gitHub, Clock clock, SyncProperties properties) {
        this.gitHub = gitHub;
        this.clock = clock;
        this.days = properties.branchScanDays();
    }

    /**
     * @param userNodeId the user's GraphQL node id, the {@code author} filter; null skips the scan
     * @param calendar   the calendar just fetched
     * @return branch commits per scanned day, oldest first. A day whose call failed is left out, so its
     * stored count stays as it was; the rest of the sync goes on.
     */
    public Map<LocalDate, Integer> scan(String token, String userNodeId, List<ContributionDay> calendar, ZoneId zone) {
        if (userNodeId == null) {
            return Map.of();
        }
        Map<LocalDate, Integer> calendarCounts = calendar.stream()
                .collect(Collectors.toMap(ContributionDay::date, ContributionDay::contributionCount, Integer::sum));
        Map<LocalDate, Integer> counts = new LinkedHashMap<>();
        for (LocalDate date : datesToScan(calendarCounts, DayWindow.today(clock.instant(), zone), days)) {
            DayWindow window = DayWindow.of(date, zone);
            try {
                counts.put(date, gitHub.countBranchCommits(token, userNodeId, window.since(), window.until()));
            } catch (GitHubApiException e) {
                log.warn("Branch scan for {} failed, its count is kept as it was: {}", date, e.getMessage());
            }
        }
        return counts;
    }

    /** The last {@code days} days up to {@code today}, oldest first, that the calendar counts as 0 or doesn't have. */
    static List<LocalDate> datesToScan(Map<LocalDate, Integer> calendar, LocalDate today, int days) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = today.minusDays(days - 1L); !date.isAfter(today); date = date.plusDays(1)) {
            if (calendar.getOrDefault(date, 0) == 0) {
                dates.add(date);
            }
        }
        return dates;
    }
}
