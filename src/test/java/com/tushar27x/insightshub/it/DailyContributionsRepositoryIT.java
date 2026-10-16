package com.tushar27x.insightshub.it;

import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import com.tushar27x.insightshub.repository.DailyContributionsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** B1's upsert against a real Postgres: what the mocked unit tests can't show (backend's B1 FYI). */
@IntegrationTest
class DailyContributionsRepositoryIT {

    static final long USER = 9_000_001L;
    static final LocalDate MON = LocalDate.of(2026, 10, 12);
    static final LocalDate TUE = MON.plusDays(1);

    @Autowired
    DailyContributionsRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void freshUser() {
        jdbc.update("DELETE FROM v2.users WHERE github_id = ?", USER);
        jdbc.update("INSERT INTO v2.users (github_id, login) VALUES (?, 'it-user')", USER);
    }

    @Test
    void upsertTwiceKeepsOneRowPerDayWithTheLatestCount() {
        repository.upsertCalendarCounts(USER, List.of(new ContributionDay(MON, 2), new ContributionDay(TUE, 0)));
        repository.upsertCalendarCounts(USER, List.of(new ContributionDay(MON, 5), new ContributionDay(TUE, 0)));

        assertThat(rows()).isEqualTo(2);
        assertThat(calendarCount(MON)).isEqualTo(5);
        assertThat(repository.latestDate(USER)).contains(TUE);
    }

    @Test
    void upsertLeavesTheBranchScanCountAlone() {
        repository.upsertCalendarCounts(USER, List.of(new ContributionDay(MON, 0)));
        jdbc.update("UPDATE v2.daily_contributions SET branch_commit_count = 3 WHERE user_id = ? AND date = ?", USER, MON);

        repository.upsertCalendarCounts(USER, List.of(new ContributionDay(MON, 1)));

        Integer branch = jdbc.queryForObject(
                "SELECT branch_commit_count FROM v2.daily_contributions WHERE user_id = ? AND date = ?",
                Integer.class, USER, MON);
        assertThat(branch).isEqualTo(3);
        assertThat(calendarCount(MON)).isEqualTo(1);
    }

    @Test
    void latestDateIsEmptyBeforeTheFirstSync() {
        assertThat(repository.latestDate(USER)).isEmpty();
    }

    private int rows() {
        return jdbc.queryForObject("SELECT count(*) FROM v2.daily_contributions WHERE user_id = ?", Integer.class, USER);
    }

    private int calendarCount(LocalDate date) {
        return jdbc.queryForObject(
                "SELECT calendar_count FROM v2.daily_contributions WHERE user_id = ? AND date = ?",
                Integer.class, USER, date);
    }
}
