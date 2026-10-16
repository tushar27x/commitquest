package com.tushar27x.insightshub.repository;

import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The SQL and parameter mapping. Running the upsert against Postgres needs Testcontainers (I2). */
class DailyContributionsRepositoryTest {

    private static final long ID = 583231L;
    private static final LocalDate OCT_13 = LocalDate.parse("2026-10-13");
    private static final LocalDate OCT_14 = LocalDate.parse("2026-10-14");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DailyContributionsRepository repository = new DailyContributionsRepository(jdbc, "v2");

    @Test
    void upsertsOneRowPerDayInASingleBatch() {
        repository.upsertCalendarCounts(ID, List.of(new ContributionDay(OCT_13, 0), new ContributionDay(OCT_14, 5)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> rows = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(repository.upsertSql), rows.capture());
        assertThat(rows.getValue()).containsExactly(
                new Object[]{ID, OCT_13, 0}, // zero days are written too: a stored 0 means "checked, nothing"
                new Object[]{ID, OCT_14, 5});
    }

    @Test
    void upsertIsIdempotentAndLeavesTheBranchScanAlone() {
        String sql = repository.upsertSql;

        assertThat(sql).contains("INSERT INTO v2.daily_contributions (user_id, date, calendar_count)")
                .contains("ON CONFLICT (user_id, date) DO UPDATE SET calendar_count = EXCLUDED.calendar_count")
                .doesNotContain("branch_commit_count");
    }

    @Test
    void everyStatementNamesTheSchemaBecauseOnlyJpaHasADefaultOne() { // B36
        assertThat(repository.latestDateSql).isEqualTo("SELECT max(date) FROM v2.daily_contributions WHERE user_id = ?");
    }

    @Test
    void schemaMustBeAPlainIdentifier() {
        assertThatThrownBy(() -> new DailyContributionsRepository(jdbc, "v2; DROP TABLE users"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyListSkipsTheDatabase() {
        repository.upsertCalendarCounts(ID, List.of());

        verify(jdbc, never()).batchUpdate(anyString(), anyList());
    }

    @Test
    void latestDateIsEmptyBeforeTheFirstSync() {
        when(jdbc.queryForObject(repository.latestDateSql, LocalDate.class, ID)).thenReturn(null);
        assertThat(repository.latestDate(ID)).isEmpty();

        when(jdbc.queryForObject(repository.latestDateSql, LocalDate.class, ID)).thenReturn(OCT_14);
        assertThat(repository.latestDate(ID)).contains(OCT_14);
    }
}
