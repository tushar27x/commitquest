package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.github.GitHubGraphQlClient;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BranchScannerTest {

    private static final LocalDate OCT_14 = LocalDate.of(2026, 10, 14);
    private static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);
    private static final LocalDate OCT_16 = LocalDate.of(2026, 10, 16);
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final String NODE = "U_node";
    private static final String TOKEN = "stored-token";

    private final GitHubGraphQlClient gitHub = mock(GitHubGraphQlClient.class);
    private final BranchScanner scanner = new BranchScanner(gitHub, Clock.fixed(NOW, ZoneOffset.UTC),
            new SyncProperties(Duration.ofMinutes(10), 4, 2));

    // datesToScan: the pure part

    @Test
    void scansTodayAndYesterdayWhenTheCalendarIsEmpty() {
        assertThat(BranchScanner.datesToScan(Map.of(), OCT_15, 2)).containsExactly(OCT_14, OCT_15);
    }

    @Test
    void skipsDaysTheCalendarAlreadyCounts() {
        Map<LocalDate, Integer> calendar = Map.of(OCT_14, 3, OCT_15, 0);

        assertThat(BranchScanner.datesToScan(calendar, OCT_15, 2)).containsExactly(OCT_15);
    }

    @Test
    void nothingToScanWhenEveryDayIsActive() {
        assertThat(BranchScanner.datesToScan(Map.of(OCT_14, 1, OCT_15, 2), OCT_15, 2)).isEmpty();
    }

    @Test
    void windowCrossesAMonthBoundary() {
        LocalDate nov1 = LocalDate.of(2026, 11, 1);

        assertThat(BranchScanner.datesToScan(Map.of(), nov1, 3))
                .containsExactly(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31), nov1);
    }

    @Test
    void zeroDaysScansNothing() {
        assertThat(BranchScanner.datesToScan(Map.of(), OCT_15, 0)).isEmpty();
    }

    // scan: GitHub calls

    @Test
    void countsEachEmptyDayInItsOwnWindow() {
        DayWindow yesterday = DayWindow.of(OCT_14, ZoneOffset.UTC);
        DayWindow today = DayWindow.of(OCT_15, ZoneOffset.UTC);
        when(gitHub.countBranchCommits(TOKEN, NODE, yesterday.since(), yesterday.until())).thenReturn(4);
        when(gitHub.countBranchCommits(TOKEN, NODE, today.since(), today.until())).thenReturn(0);

        Map<LocalDate, Integer> counts = scanner.scan(TOKEN, NODE, List.of(new ContributionDay(OCT_15, 0)), ZoneOffset.UTC);

        assertThat(counts).containsExactly(Map.entry(OCT_14, 4), Map.entry(OCT_15, 0));
    }

    @Test
    void todayAndTheWindowsFollowTheUsersZone() {
        ZoneId kolkata = ZoneId.of("Asia/Kolkata"); // NOW is 17:30 on Oct 15 there
        DayWindow today = DayWindow.of(OCT_15, kolkata);
        when(gitHub.countBranchCommits(TOKEN, NODE, today.since(), today.until())).thenReturn(1);

        Map<LocalDate, Integer> counts = scanner.scan(TOKEN, NODE, List.of(new ContributionDay(OCT_14, 2)), kolkata);

        assertThat(counts).containsExactly(Map.entry(OCT_15, 1));
    }

    @Test
    void activeCalendarMeansNoGitHubCalls() {
        List<ContributionDay> calendar = List.of(new ContributionDay(OCT_14, 1), new ContributionDay(OCT_15, 5));

        assertThat(scanner.scan(TOKEN, NODE, calendar, ZoneOffset.UTC)).isEmpty();
        verifyNoInteractions(gitHub);
    }

    @Test
    void withoutANodeIdThereIsNothingToFilterOn() {
        assertThat(scanner.scan(TOKEN, null, List.of(), ZoneOffset.UTC)).isEmpty();
        verifyNoInteractions(gitHub);
    }

    @Test
    void aFailedDayIsLeftOutAndTheOthersAreKept() {
        DayWindow yesterday = DayWindow.of(OCT_14, ZoneOffset.UTC);
        DayWindow today = DayWindow.of(OCT_15, ZoneOffset.UTC);
        when(gitHub.countBranchCommits(TOKEN, NODE, yesterday.since(), yesterday.until()))
                .thenThrow(new GitHubApiException("boom"));
        when(gitHub.countBranchCommits(TOKEN, NODE, today.since(), today.until())).thenReturn(2);

        assertThat(scanner.scan(TOKEN, NODE, List.of(), ZoneOffset.UTC)).containsExactly(Map.entry(OCT_15, 2));
    }

    @Test
    void tomorrowInTheCalendarDoesntChangeWhatIsScanned() {
        // A commit stamped with a zone ahead of the user's can land on "tomorrow" in GitHub's calendar
        when(gitHub.countBranchCommits(anyString(), anyString(), any(), any())).thenReturn(0);

        scanner.scan(TOKEN, NODE, List.of(new ContributionDay(OCT_16, 7)), ZoneOffset.UTC);

        DayWindow yesterday = DayWindow.of(OCT_14, ZoneOffset.UTC);
        verify(gitHub).countBranchCommits(TOKEN, NODE, yesterday.since(), yesterday.until());
    }
}
