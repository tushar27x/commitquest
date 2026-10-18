package com.tushar27x.insightshub.repository;

import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The SQL and parameter mapping; running it against Postgres is an IT (B36's lesson). */
class HeroRepositoryTest {

    private static final long ID = 583231L;
    private static final LocalDate OCT_14 = LocalDate.parse("2026-10-14");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final HeroRepository repository = new HeroRepository(jdbc, JsonMapper.builder().build(), "v2");

    @Test
    void everyTableIsQualifiedWithTheSchema() {
        for (String sql : List.of(repository.findStateSql, repository.insertStateSql, repository.updateStateSql,
                repository.insertEventSql, repository.insertDaySql, repository.findEventsSql, repository.findDaysSql)) {
            assertThat(sql).containsPattern("v2\\.hero_(state|events|days)");
        }
        assertThat(repository.findCandidatesSql).contains("v2.hero_state h JOIN v2.users u");
    }

    @Test
    void writesNeverAddARowTwice() {
        assertThat(repository.insertStateSql).contains("ON CONFLICT (user_id) DO NOTHING");
        assertThat(repository.insertEventSql).contains("ON CONFLICT (user_id, type, on_date) DO NOTHING");
        assertThat(repository.insertDaySql).contains("ON CONFLICT (user_id, on_date) DO NOTHING");
    }

    @Test
    void theStateMovesOnlyFromWhereItWasRead() {
        assertThat(repository.updateStateSql).contains("WHERE user_id = ? AND last_evaluated_on = ?");
    }

    @Test
    void eventsKeepTheRulesOrderWithinADay() {
        assertThat(repository.findEventsSql).contains("ORDER BY on_date, id");
    }

    @Test
    void eventDetailsAreWrittenAsJsonInListOrder() {
        repository.insertEvents(ID, List.of(
                new HeroEvent(HeroEventType.LIFE_LOST, OCT_14, Map.of("lives", 0, "reason", "NO_ACTIVITY")),
                new HeroEvent(HeroEventType.FAINTED, OCT_14, Map.of())));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> rows = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(repository.insertEventSql), rows.capture());
        assertThat(rows.getValue()).hasSize(2);
        assertThat(rows.getValue().get(0)).startsWith(ID, "LIFE_LOST", OCT_14);
        assertThat((String) rows.getValue().get(0)[3]).contains("\"lives\":0").contains("\"reason\":\"NO_ACTIVITY\"");
        assertThat(rows.getValue().get(1)).containsExactly(ID, "FAINTED", OCT_14, "{}");
    }

    @Test
    void nothingToWriteMakesNoCall() {
        repository.insertEvents(ID, List.of());
        repository.insertDays(ID, List.of());

        verify(jdbc, never()).batchUpdate(anyString(), anyList());
    }

    @Test
    void theSchemaMustBeAPlainIdentifier() {
        assertThatThrownBy(() -> new HeroRepository(jdbc, JsonMapper.builder().build(), "v2; DROP TABLE users"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
