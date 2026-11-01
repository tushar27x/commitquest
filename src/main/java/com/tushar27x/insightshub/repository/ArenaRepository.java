package com.tushar27x.insightshub.repository;

import com.tushar27x.insightshub.entity.Archetype;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * The Arena (Phase 13): heroes ranked by {@code level DESC, xp DESC} within one class, computed in Postgres with a
 * window function on every request (no stored rank, no Redis mirror: the plan ranks by hero level, which the hourly
 * job changes, and the tables are small). Plain JDBC like {@link HeroRepository}; tables qualified with the Flyway
 * schema (B36).
 * <p>
 * Who is on a board: a hero ({@code hero_state}) whose user has {@code public_profile} on and isn't
 * {@code needs_reauth} (the hero job skips those, so their hero would sit frozen on the board), and whose
 * <b>current</b> class, {@code chosen_archetype ?? insights.archetype}, is the board's class.
 * <p>
 * Ties share a rank ({@code RANK()}: 1, 2, 2, 4); within a tie, rows are listed by GitHub id (the older account
 * first), so pages never overlap or skip a row.
 */
@Repository
public class ArenaRepository {

    /** Who is on the board of one class (parameter 1); see the class comment. */
    private static final String BOARD = """
            FROM %1$s.hero_state h
            JOIN %1$s.users u ON u.github_id = h.user_id
            JOIN %1$s.insights i ON i.user_id = h.user_id
            WHERE u.public_profile AND NOT u.needs_reauth
              AND COALESCE(u.chosen_archetype, i.archetype) = ?
            """;
    /** The board with each hero's rank and the board's size. */
    private static final String RANKED = """
            SELECT h.user_id, u.login, h.level, h.xp, h.lives,
                   RANK() OVER (ORDER BY h.level DESC, h.xp DESC) AS rank,
                   COUNT(*) OVER () AS total
            %s""";
    private static final String PAGE = """
            WITH ranked AS (%s)
            SELECT user_id, login, level, xp, lives, rank FROM ranked
            ORDER BY rank, user_id
            LIMIT ? OFFSET ?
            """;
    private static final String COUNT = """
            SELECT COUNT(*) %s""";
    private static final String RANK_OF = """
            WITH ranked AS (%s)
            SELECT rank, total FROM ranked WHERE user_id = ?
            """;
    /** The caller's own standing, whether or not they're on a board. Empty without a hero and insights. */
    private static final String STANDING = """
            SELECT u.public_profile, u.needs_reauth, COALESCE(u.chosen_archetype, i.archetype) AS archetype
            FROM %1$s.users u
            JOIN %1$s.insights i ON i.user_id = u.github_id
            JOIN %1$s.hero_state h ON h.user_id = u.github_id
            WHERE u.github_id = ?
            """;

    private final JdbcTemplate jdbc;
    final String pageSql;
    final String countSql;
    final String rankOfSql;
    final String standingSql;

    /** @param schema Flyway's schema, the one JPA also uses ({@code v2}) */
    public ArenaRepository(JdbcTemplate jdbc, @Value("${spring.flyway.default-schema}") String schema) {
        if (!schema.matches("[a-z_][a-z0-9_]*")) { // it goes into SQL text, so only a plain identifier
            throw new IllegalArgumentException("Invalid schema name: " + schema);
        }
        this.jdbc = jdbc;
        String board = BOARD.formatted(schema);
        String ranked = RANKED.formatted(board);
        this.pageSql = PAGE.formatted(ranked);
        this.countSql = COUNT.formatted(board);
        this.rankOfSql = RANK_OF.formatted(ranked);
        this.standingSql = STANDING.formatted(schema);
    }

    /** One page of a class's board, best first; {@code offset} is in rows. */
    public List<Row> page(Archetype archetype, int limit, long offset) {
        return jdbc.query(pageSql, (rs, n) -> new Row(rs.getLong("user_id"), rs.getString("login"),
                rs.getInt("level"), rs.getInt("xp"), rs.getInt("lives"), rs.getInt("rank")),
                archetype.name(), limit, offset);
    }

    /** How many heroes are on a class's board. */
    public int count(Archetype archetype) {
        Integer count = jdbc.queryForObject(countSql, Integer.class, archetype.name());
        return count == null ? 0 : count;
    }

    /** The user's rank on a class's board; empty if they aren't on it. */
    public Optional<Rank> rankOf(long userId, Archetype archetype) {
        return jdbc.query(rankOfSql, (rs, n) -> new Rank(rs.getInt("rank"), rs.getInt("total")),
                archetype.name(), userId).stream().findFirst();
    }

    /** The user's visibility and current class; empty before the first sync (no hero or insights yet). */
    public Optional<Standing> standing(long userId) {
        return jdbc.query(standingSql, (rs, n) -> {
            String archetype = rs.getString("archetype");
            return new Standing(rs.getBoolean("public_profile"), rs.getBoolean("needs_reauth"),
                    archetype == null ? null : Archetype.valueOf(archetype));
        }, userId).stream().filter(standing -> standing.archetype() != null).findFirst();
    }

    public record Row(long userId, String login, int level, int xp, int lives, int rank) {
    }

    public record Rank(int rank, int total) {
    }

    /** @param archetype the current class ({@code chosen ?? scored}), never null */
    public record Standing(boolean publicProfile, boolean needsReauth, Archetype archetype) {
    }
}
