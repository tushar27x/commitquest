package com.tushar27x.insightshub.repository;

import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;
import com.tushar27x.insightshub.hero.HeroState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code hero_state}, {@code hero_events} and {@code hero_days} (V5), in plain JDBC like
 * {@link DailyContributionsRepository}: the writes are {@code INSERT ... ON CONFLICT DO NOTHING} on the tables'
 * unique keys, so settling a day twice can't add a row twice. Tables are qualified with the Flyway schema (B36).
 */
@Repository
public class HeroRepository {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private static final String FIND_STATE = """
            SELECT level, xp, lives, streak, last_evaluated_on FROM %s.hero_state WHERE user_id = ?
            """;
    private static final String INSERT_STATE = """
            INSERT INTO %s.hero_state (user_id, level, xp, lives, streak, last_evaluated_on, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id) DO NOTHING
            """;
    /** Compare-and-set on {@code last_evaluated_on}: only the run that read the old state can move it. */
    private static final String UPDATE_STATE = """
            UPDATE %s.hero_state SET level = ?, xp = ?, lives = ?, streak = ?, last_evaluated_on = ?, updated_at = ?
            WHERE user_id = ? AND last_evaluated_on = ?
            """;
    private static final String INSERT_EVENT = """
            INSERT INTO %s.hero_events (user_id, type, on_date, details)
            VALUES (?, ?, ?, CAST(? AS jsonb))
            ON CONFLICT (user_id, type, on_date) DO NOTHING
            """;
    private static final String INSERT_DAY = """
            INSERT INTO %s.hero_days (user_id, on_date, xp_gained, streak)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, on_date) DO NOTHING
            """;
    /** {@code id} keeps the order the rules emitted them in within a day (LEVEL_UP before LIFE_GAINED, ...). */
    private static final String FIND_EVENTS = """
            SELECT type, on_date, details::text AS details FROM %s.hero_events
            WHERE user_id = ? AND on_date BETWEEN ? AND ?
            ORDER BY on_date, id
            """;
    private static final String FIND_DAYS = """
            SELECT on_date, xp_gained, streak FROM %s.hero_days
            WHERE user_id = ? AND on_date BETWEEN ? AND ?
            ORDER BY on_date
            """;
    /** A cheap first cut for the hourly job; {@code HeroRules.dueDays} decides per user's zone. */
    private static final String FIND_CANDIDATES = """
            SELECT h.user_id, u.timezone, h.last_evaluated_on
            FROM %1$s.hero_state h JOIN %1$s.users u ON u.github_id = h.user_id
            WHERE h.last_evaluated_on < ?
            ORDER BY h.user_id
            """;

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    final String findStateSql;
    final String insertStateSql;
    final String updateStateSql;
    final String insertEventSql;
    final String insertDaySql;
    final String findEventsSql;
    final String findDaysSql;
    final String findCandidatesSql;

    /** @param schema Flyway's schema, the one JPA also uses ({@code v2}) */
    public HeroRepository(JdbcTemplate jdbc, JsonMapper jsonMapper,
                          @Value("${spring.flyway.default-schema}") String schema) {
        if (!schema.matches("[a-z_][a-z0-9_]*")) { // it goes into SQL text, so only a plain identifier
            throw new IllegalArgumentException("Invalid schema name: " + schema);
        }
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.findStateSql = FIND_STATE.formatted(schema);
        this.insertStateSql = INSERT_STATE.formatted(schema);
        this.updateStateSql = UPDATE_STATE.formatted(schema);
        this.insertEventSql = INSERT_EVENT.formatted(schema);
        this.insertDaySql = INSERT_DAY.formatted(schema);
        this.findEventsSql = FIND_EVENTS.formatted(schema);
        this.findDaysSql = FIND_DAYS.formatted(schema);
        this.findCandidatesSql = FIND_CANDIDATES.formatted(schema);
    }

    public Optional<HeroState> findState(long userId) {
        return jdbc.query(findStateSql, (rs, row) -> new HeroState(rs.getInt("level"), rs.getInt("xp"),
                rs.getInt("lives"), rs.getInt("streak"), rs.getObject("last_evaluated_on", LocalDate.class)), userId)
                .stream().findFirst();
    }

    /** @return false if the user already had a hero (nothing changed) */
    public boolean insertState(long userId, HeroState state, Instant now) {
        return jdbc.update(insertStateSql, userId, state.level(), state.xp(), state.lives(), state.streak(),
                state.lastEvaluatedOn(), utc(now)) == 1;
    }

    /** @return false if {@code last_evaluated_on} is no longer {@code expectedLastEvaluatedOn} (another run won) */
    public boolean updateState(long userId, HeroState state, LocalDate expectedLastEvaluatedOn, Instant now) {
        return jdbc.update(updateStateSql, state.level(), state.xp(), state.lives(), state.streak(),
                state.lastEvaluatedOn(), utc(now), userId, expectedLastEvaluatedOn) == 1;
    }

    /** In list order, so ids keep the rules' order. An event already stored is skipped. */
    public void insertEvents(long userId, List<HeroEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(insertEventSql, events.stream()
                .map(event -> new Object[]{userId, event.type().name(), event.onDate(),
                        jsonMapper.writeValueAsString(event.details())})
                .toList());
    }

    /** One row per settled day; a day already stored is skipped. */
    public void insertDays(long userId, List<SettledDay> days) {
        if (days.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(insertDaySql, days.stream()
                .map(day -> new Object[]{userId, day.onDate(), day.xpGained(), day.streak()})
                .toList());
    }

    /** Events on {@code [from, to]}, oldest first, in the order they were stored within a day. */
    public List<HeroEvent> findEvents(long userId, LocalDate from, LocalDate to) {
        return jdbc.query(findEventsSql, (rs, row) -> new HeroEvent(HeroEventType.valueOf(rs.getString("type")),
                rs.getObject("on_date", LocalDate.class), jsonMapper.readValue(rs.getString("details"), MAP)),
                userId, from, to);
    }

    /** Settled days on {@code [from, to]}, oldest first. */
    public List<SettledDay> findDays(long userId, LocalDate from, LocalDate to) {
        return jdbc.query(findDaysSql, (rs, row) -> new SettledDay(rs.getObject("on_date", LocalDate.class),
                rs.getInt("xp_gained"), rs.getInt("streak")), userId, from, to);
    }

    /** Heroes whose {@code last_evaluated_on} is before {@code before}, with the user's zone. */
    public List<Candidate> findCandidates(LocalDate before) {
        return jdbc.query(findCandidatesSql, (rs, row) -> new Candidate(rs.getLong("user_id"),
                rs.getString("timezone"), rs.getObject("last_evaluated_on", LocalDate.class)), before);
    }

    /** PgJDBC binds {@code OffsetDateTime} to {@code timestamptz}, not {@code Instant}. */
    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    /** A row of {@code hero_days}: the XP the day gave and the streak after it. */
    public record SettledDay(LocalDate onDate, int xpGained, int streak) {
    }

    public record Candidate(long userId, String timezone, LocalDate lastEvaluatedOn) {
    }
}
