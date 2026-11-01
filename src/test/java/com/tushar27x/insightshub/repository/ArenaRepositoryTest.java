package com.tushar27x.insightshub.repository;

import com.tushar27x.insightshub.entity.Archetype;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The SQL text and parameter order; running the ranking against Postgres is {@code it/ArenaIT}. */
class ArenaRepositoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ArenaRepository repository = new ArenaRepository(jdbc, "v2");

    @Test
    void everyTableIsQualifiedWithTheSchema() {
        for (String sql : List.of(repository.pageSql, repository.countSql, repository.rankOfSql)) {
            assertThat(sql).contains("FROM v2.hero_state h", "JOIN v2.users u", "JOIN v2.insights i")
                    .doesNotContain("%");
        }
        assertThat(repository.standingSql).contains("FROM v2.users u", "JOIN v2.insights i", "JOIN v2.hero_state h");
    }

    @Test
    void onlyPublicHeroesWithAWorkingTokenInTheirCurrentClassAreOnABoard() {
        for (String sql : List.of(repository.pageSql, repository.countSql, repository.rankOfSql)) {
            assertThat(sql).contains("WHERE u.public_profile AND NOT u.needs_reauth",
                    "COALESCE(u.chosen_archetype, i.archetype) = ?");
        }
    }

    @Test
    void ranksByLevelThenXpWithTiesSharedAndListedByGitHubId() {
        assertThat(repository.pageSql).contains("RANK() OVER (ORDER BY h.level DESC, h.xp DESC)",
                "ORDER BY rank, user_id", "LIMIT ? OFFSET ?");
        assertThat(repository.rankOfSql).contains("RANK() OVER (ORDER BY h.level DESC, h.xp DESC)",
                "COUNT(*) OVER ()", "WHERE user_id = ?");
    }

    @Test
    void aPageBindsTheClassThenLimitAndOffset() {
        repository.page(Archetype.MONK, 20, 4_294_967_280L);

        verify(jdbc).query(eq(repository.pageSql), any(RowMapper.class), eq("MONK"), eq(20), eq(4_294_967_280L));
    }

    @Test
    void theRankBindsTheClassBeforeTheUser() {
        repository.rankOf(583231L, Archetype.ROGUE);

        verify(jdbc).query(eq(repository.rankOfSql), any(RowMapper.class), eq("ROGUE"), eq(583231L));
    }

    @Test
    void theSchemaMustBeAPlainIdentifier() {
        assertThatThrownBy(() -> new ArenaRepository(jdbc, "v2; DROP TABLE users"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
