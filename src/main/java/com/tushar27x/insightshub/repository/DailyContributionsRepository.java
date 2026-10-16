package com.tushar27x.insightshub.repository;

import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code daily_contributions}, written with plain JDBC instead of JPA: {@code save()} would SELECT
 * each row before its INSERT or UPDATE, while one batched {@code INSERT ... ON CONFLICT} writes a
 * year of days in a single round trip and is idempotent on the {@code (user_id, date)} key.
 */
@Repository
public class DailyContributionsRepository {

    /** Only {@code calendar_count} is overwritten: {@code branch_commit_count} belongs to the branch scan. */
    static final String UPSERT_CALENDAR_COUNT = """
            INSERT INTO daily_contributions (user_id, date, calendar_count)
            VALUES (?, ?, ?)
            ON CONFLICT (user_id, date) DO UPDATE SET calendar_count = EXCLUDED.calendar_count
            """;

    static final String LATEST_DATE = "SELECT max(date) FROM daily_contributions WHERE user_id = ?";

    private final JdbcTemplate jdbc;

    public DailyContributionsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Upserts {@code calendar_count} for each day; rows for days not in the list are left alone. */
    public void upsertCalendarCounts(long userId, List<ContributionDay> days) {
        if (days.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(UPSERT_CALENDAR_COUNT, rows(userId, days));
    }

    /** The most recent stored day, or empty before the user's first sync. */
    public Optional<LocalDate> latestDate(long userId) {
        return Optional.ofNullable(jdbc.queryForObject(LATEST_DATE, LocalDate.class, userId));
    }

    static List<Object[]> rows(long userId, List<ContributionDay> days) {
        return days.stream()
                .map(day -> new Object[]{userId, day.date(), day.contributionCount()})
                .toList();
    }
}
