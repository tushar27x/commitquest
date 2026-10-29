package com.tushar27x.insightshub.repository;

import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;
import com.tushar27x.insightshub.notify.DigestPlan.UnsentEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * {@code digests} (V6) and the digest's side of {@code hero_events}, in plain JDBC like {@link HeroRepository}.
 * Tables are qualified with the Flyway schema (B36).
 */
@Repository
public class DigestRepository {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    /** Users with at least one unsent event (the partial index {@code idx_hero_events_unsent}), with what the job needs. */
    private static final String FIND_CANDIDATES = """
            SELECT u.github_id, u.login, u.email, u.timezone, u.alerts_enabled, u.needs_reauth,
                   (SELECT max(d.created_at) FROM %1$s.digests d
                    WHERE d.user_id = u.github_id AND d.status = 'SENT') AS last_sent_at
            FROM %1$s.users u
            WHERE EXISTS (SELECT 1 FROM %1$s.hero_events e WHERE e.user_id = u.github_id AND e.digest_id IS NULL)
            ORDER BY u.github_id
            """;
    private static final String FIND_UNSENT = """
            SELECT id, type, on_date, details::text AS details FROM %s.hero_events
            WHERE user_id = ? AND digest_id IS NULL
            ORDER BY on_date, id
            """;
    private static final String INSERT_DIGEST = """
            INSERT INTO %s.digests (user_id, status, channel, event_count, ai_intro, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id
            """;
    /** Only events still unsent: a second run can't re-stamp what the first already covered. */
    private static final String STAMP = """
            UPDATE %s.hero_events SET digest_id = ?
            WHERE user_id = ? AND digest_id IS NULL AND id = ANY (?)
            """;

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    final String findCandidatesSql;
    final String findUnsentSql;
    final String insertDigestSql;
    final String stampSql;

    /** @param schema Flyway's schema, the one JPA also uses ({@code v2}) */
    public DigestRepository(JdbcTemplate jdbc, JsonMapper jsonMapper,
                            @Value("${spring.flyway.default-schema}") String schema) {
        if (!schema.matches("[a-z_][a-z0-9_]*")) { // it goes into SQL text, so only a plain identifier
            throw new IllegalArgumentException("Invalid schema name: " + schema);
        }
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.findCandidatesSql = FIND_CANDIDATES.formatted(schema);
        this.findUnsentSql = FIND_UNSENT.formatted(schema);
        this.insertDigestSql = INSERT_DIGEST.formatted(schema);
        this.stampSql = STAMP.formatted(schema);
    }

    public List<Candidate> findCandidates() {
        return jdbc.query(findCandidatesSql, (rs, row) -> {
            Timestamp lastSent = rs.getTimestamp("last_sent_at");
            return new Candidate(rs.getLong("github_id"), rs.getString("login"), rs.getString("email"),
                    rs.getString("timezone"), rs.getBoolean("alerts_enabled"), rs.getBoolean("needs_reauth"),
                    lastSent == null ? null : lastSent.toInstant());
        });
    }

    /** The user's unsent events, oldest first, in the order they were stored within a day. */
    public List<UnsentEvent> findUnsent(long userId) {
        return jdbc.query(findUnsentSql, (rs, row) -> new UnsentEvent(rs.getLong("id"),
                new HeroEvent(HeroEventType.valueOf(rs.getString("type")), rs.getObject("on_date", LocalDate.class),
                        jsonMapper.readValue(rs.getString("details"), MAP))), userId);
    }

    /** @param channel null for {@link Status#SKIPPED} */
    public long insertDigest(long userId, Status status, String channel, int eventCount, boolean aiIntro, Instant at) {
        Long id = jdbc.queryForObject(insertDigestSql, Long.class, userId, status.name(), channel, eventCount, aiIntro,
                utc(at));
        if (id == null) {
            throw new IllegalStateException("INSERT ... RETURNING id returned nothing");
        }
        return id;
    }

    /** @return how many of {@code eventIds} were still unsent and now belong to the digest */
    public int stamp(long digestId, long userId, List<Long> eventIds) {
        Integer stamped = jdbc.execute(stampSql, (PreparedStatementCallback<Integer>) statement -> {
            statement.setLong(1, digestId);
            statement.setLong(2, userId);
            statement.setArray(3, statement.getConnection().createArrayOf("bigint", eventIds.toArray()));
            return statement.executeUpdate();
        });
        return stamped == null ? 0 : stamped;
    }

    /** PgJDBC binds {@code OffsetDateTime} to {@code timestamptz}, not {@code Instant}. */
    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    public enum Status {
        SENT, SKIPPED
    }

    /** @param lastSentAt the latest {@code SENT} digest, or null */
    public record Candidate(long userId, String login, String email, String timezone, boolean alertsEnabled,
                            boolean needsReauth, Instant lastSentAt) {

        /** Alerts on, somewhere to send them, and a token GitHub accepts (as the hero job requires). */
        public boolean eligible() {
            return alertsEnabled && email != null && !email.isBlank() && !needsReauth;
        }
    }
}
