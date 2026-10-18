package com.tushar27x.insightshub.repository;

import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

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

    /**
     * Only {@code branch_commit_count} is written, and it never goes down: commits seen once stay seen,
     * even if their branch is deleted after a squash merge or rewritten by a force-push.
     */
    private static final String UPSERT_BRANCH_COUNT = """
            INSERT INTO %s.daily_contributions AS d (user_id, date, branch_commit_count)
            VALUES (?, ?, ?)
            ON CONFLICT (user_id, date) DO UPDATE
            SET branch_commit_count = GREATEST(d.branch_commit_count, EXCLUDED.branch_commit_count)
            """;

    private static final String LATEST_DATE = "SELECT max(date) FROM %s.daily_contributions WHERE user_id = ?";

    private static final String FIND_COUNTS = """
            SELECT date, calendar_count, branch_commit_count FROM %s.daily_contributions
            WHERE user_id = ? AND date BETWEEN ? AND ?
            """;

    private final JdbcTemplate jdbc;
    final String upsertSql;
    final String upsertBranchSql;
    final String latestDateSql;
    final String findCountsSql;

    /** @param schema Flyway's schema, the one JPA also uses ({@code v2}) */
    public DailyContributionsRepository(JdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        if (!schema.matches("[a-z_][a-z0-9_]*")) { // it goes into SQL text, so only a plain identifier
            throw new IllegalArgumentException("Invalid schema name: " + schema);
        }
        this.jdbc = jdbc;
        this.upsertSql = UPSERT_CALENDAR_COUNT.formatted(schema);
        this.upsertBranchSql = UPSERT_BRANCH_COUNT.formatted(schema);
        this.latestDateSql = LATEST_DATE.formatted(schema);
        this.findCountsSql = FIND_COUNTS.formatted(schema);
    }

    /** Upserts {@code calendar_count} for each day; rows for days not in the list are left alone. */
    public void upsertCalendarCounts(long userId, List<ContributionDay> days) {
        if (days.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(upsertSql, rows(userId, days));
    }

    /** Raises {@code branch_commit_count} to each day's count (inserting the day if it's new); never lowers it. */
    public void upsertBranchCounts(long userId, Map<LocalDate, Integer> counts) {
        if (counts.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(upsertBranchSql, branchRows(userId, counts));
    }

    /** The most recent stored day, or empty before the user's first sync. */
    public Optional<LocalDate> latestDate(long userId) {
        return Optional.ofNullable(jdbc.queryForObject(latestDateSql, LocalDate.class, userId));
    }

    /** The stored days in {@code [from, to]}; a day without a row had no activity seen yet. */
    public Map<LocalDate, DayCounts> findCounts(long userId, LocalDate from, LocalDate to) {
        Map<LocalDate, DayCounts> counts = new TreeMap<>();
        jdbc.query(findCountsSql, rs -> {
            counts.put(rs.getObject("date", LocalDate.class),
                    new DayCounts(rs.getInt("calendar_count"), rs.getInt("branch_commit_count")));
        }, userId, from, to);
        return counts;
    }

    static List<Object[]> rows(long userId, List<ContributionDay> days) {
        return days.stream()
                .map(day -> new Object[]{userId, day.date(), day.contributionCount()})
                .toList();
    }

    static List<Object[]> branchRows(long userId, Map<LocalDate, Integer> counts) {
        return counts.entrySet().stream()
                .map(day -> new Object[]{userId, day.getKey(), day.getValue()})
                .toList();
    }

    /** One day's activity: the hero counts a day as fed when either is above 0. */
    public record DayCounts(int calendarCount, int branchCommitCount) {

        public static final DayCounts NONE = new DayCounts(0, 0);

        public boolean active() {
            return calendarCount > 0 || branchCommitCount > 0;
        }
    }
}
