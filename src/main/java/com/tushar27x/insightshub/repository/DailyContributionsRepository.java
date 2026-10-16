package com.tushar27x.insightshub.repository;

import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@code daily_contributions}, written with plain JDBC instead of JPA: {@code save()} would SELECT
 * each row before its INSERT or UPDATE, while one batched {@code INSERT ... ON CONFLICT} writes a
 * year of days in a single round trip and is idempotent on the {@code (user_id, date)} key.
 * <p>
 * The table name is schema-qualified (B36): only JPA has {@code default_schema: v2}, and plain JDBC
 * would look in {@code public}. Qualifying beats setting {@code search_path} on the connection,
 * because Neon's pooler (PgBouncer, transaction mode) doesn't reliably keep session settings.
 */
@Repository
public class DailyContributionsRepository {

    /** Only {@code calendar_count} is overwritten: {@code branch_commit_count} belongs to the branch scan. */
    private static final String UPSERT_CALENDAR_COUNT = """
            INSERT INTO %s.daily_contributions (user_id, date, calendar_count)
            VALUES (?, ?, ?)
            ON CONFLICT (user_id, date) DO UPDATE SET calendar_count = EXCLUDED.calendar_count
            """;

    private static final String LATEST_DATE = "SELECT max(date) FROM %s.daily_contributions WHERE user_id = ?";

    private final JdbcTemplate jdbc;
    final String upsertSql;
    final String latestDateSql;

    /** @param schema Flyway's schema, the one JPA also uses ({@code v2}) */
    public DailyContributionsRepository(JdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        if (!schema.matches("[a-z_][a-z0-9_]*")) { // it goes into SQL text, so only a plain identifier
            throw new IllegalArgumentException("Invalid schema name: " + schema);
        }
        this.jdbc = jdbc;
        this.upsertSql = UPSERT_CALENDAR_COUNT.formatted(schema);
        this.latestDateSql = LATEST_DATE.formatted(schema);
    }

    /** Upserts {@code calendar_count} for each day; rows for days not in the list are left alone. */
    public void upsertCalendarCounts(long userId, List<ContributionDay> days) {
        if (days.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(upsertSql, rows(userId, days));
    }

    /** The most recent stored day, or empty before the user's first sync. */
    public Optional<LocalDate> latestDate(long userId) {
        return Optional.ofNullable(jdbc.queryForObject(latestDateSql, LocalDate.class, userId));
    }

    static List<Object[]> rows(long userId, List<ContributionDay> days) {
        return days.stream()
                .map(day -> new Object[]{userId, day.date(), day.contributionCount()})
                .toList();
    }
}
