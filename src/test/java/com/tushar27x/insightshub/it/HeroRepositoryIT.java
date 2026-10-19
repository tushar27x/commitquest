package com.tushar27x.insightshub.it;

import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;
import com.tushar27x.insightshub.hero.HeroRules;
import com.tushar27x.insightshub.hero.HeroService;
import com.tushar27x.insightshub.hero.HeroState;
import com.tushar27x.insightshub.repository.HeroRepository;
import com.tushar27x.insightshub.repository.HeroRepository.Candidate;
import com.tushar27x.insightshub.repository.HeroRepository.SettledDay;
import com.tushar27x.insightshub.user.Preferences;
import com.tushar27x.insightshub.user.PreferencesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** B5's SQL on a real Postgres: the hero job's writes are idempotent, reads come back in order. */
@IntegrationTest
class HeroRepositoryIT {

    static final long USER = 9_000_301L; // block 9_000_3xx, see IntegrationTest

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    HeroRepository heroes;
    @Autowired
    HeroService heroService;
    @Autowired
    PreferencesService preferencesService;

    @BeforeEach
    void freshUser() {
        jdbc.update("DELETE FROM v2.users WHERE github_id = ?", USER);
        jdbc.update("INSERT INTO v2.users (github_id, login, created_at) VALUES (?, 'it-hero-job', now() - interval '30 days')",
                USER);
    }

    @Test
    void seedOnceThenCatchUpWritesEachDayOnce() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC); // the user's zone is the default, UTC
        jdbc.update("INSERT INTO v2.daily_contributions (user_id, date, calendar_count) VALUES (?, ?, 3)",
                USER, today.minusDays(5));
        assertThat(heroService.seedIfMissing(USER)).isTrue();
        assertThat(heroService.seedIfMissing(USER)).isFalse();

        // The job was down: last settled 4 days ago. Three days ago was active (a branch commit), then nothing.
        LocalDate lastSettled = today.minusDays(4);
        jdbc.update("UPDATE v2.hero_state SET last_evaluated_on = ? WHERE user_id = ?", lastSettled, USER);
        jdbc.update("INSERT INTO v2.daily_contributions (user_id, date, branch_commit_count) VALUES (?, ?, 1)",
                USER, today.minusDays(3));
        List<LocalDate> due = HeroRules.dueDays(lastSettled, Instant.now(), ZoneOffset.UTC); // 2 or 3, by the 06:00 grace

        assertThat(heroService.evaluate(USER)).isEqualTo(due.size());
        assertThat(heroService.evaluate(USER)).isZero(); // a second run writes nothing

        List<SettledDay> days = heroes.findDays(USER, today.minusDays(10), today);
        assertThat(days).extracting(SettledDay::onDate).containsExactlyElementsOf(due);
        assertThat(days.getFirst().xpGained()).isEqualTo(10);
        assertThat(days.subList(1, days.size())).allSatisfy(d -> assertThat(d.xpGained()).isZero());

        // Oldest first, JSONB details read back: one heart lost per inactive day, 3 → 2 (→ 1).
        List<HeroEvent> events = heroes.findEvents(USER, today.minusDays(10), today);
        assertThat(events).extracting(HeroEvent::type).containsOnly(HeroEventType.LIFE_LOST).hasSize(due.size() - 1);
        assertThat(events.getFirst().onDate()).isEqualTo(today.minusDays(2));
        assertThat(events.getFirst().details()).containsEntry("lives", 2).containsEntry("reason", "NO_ACTIVITY");

        HeroState state = heroes.findState(USER).orElseThrow();
        assertThat(state.lastEvaluatedOn()).isEqualTo(due.getLast());
        // The compare-and-set refuses a stale last_evaluated_on.
        assertThat(heroes.updateState(USER, state, state.lastEvaluatedOn().minusDays(1), Instant.now())).isFalse();
        assertThat(heroes.findCandidates(today.plusDays(1))).extracting(Candidate::userId).contains(USER);
        assertThat(heroes.findCandidates(today.minusDays(30))).extracting(Candidate::userId).doesNotContain(USER);
    }

    @Test
    void preferencesArePersistedAndRestDaysApplyFromTomorrow() {
        preferencesService.update(USER, new Preferences("Asia/Kolkata", List.of(DayOfWeek.SUNDAY), true, true));

        assertThat(jdbc.queryForMap("SELECT timezone, rest_days, rest_days_previous, alerts_enabled, public_profile "
                + "FROM v2.users WHERE github_id = ?", USER))
                .containsEntry("timezone", "Asia/Kolkata")
                .containsEntry("rest_days", 64) // bit 6 = Sunday
                .containsEntry("rest_days_previous", 0)
                .containsEntry("alerts_enabled", true)
                .containsEntry("public_profile", true);
        assertThat(jdbc.queryForObject("SELECT rest_days_from FROM v2.users WHERE github_id = ?", LocalDate.class, USER))
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1));
        assertThat(preferencesService.get(USER).orElseThrow().restDays()).containsExactly(DayOfWeek.SUNDAY);
    }
}
