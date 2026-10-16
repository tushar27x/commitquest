package com.tushar27x.insightshub.it;

import com.tushar27x.insightshub.hero.HeroRules;
import com.tushar27x.insightshub.hero.RestDays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V5's CHECKs and keys on a real Postgres: they must refuse exactly what HeroState and RestDays refuse (B4). */
@IntegrationTest
class HeroSchemaIT {

    static final long USER = 9_000_201L; // block 9_000_2xx, see IntegrationTest

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void freshUser() {
        jdbc.update("DELETE FROM v2.users WHERE github_id = ?", USER);
        jdbc.update("INSERT INTO v2.users (github_id, login) VALUES (?, 'it-hero')", USER);
    }

    @Test
    void newUsersGetUtcAndNoRestDays() {
        assertThat(jdbc.queryForMap("SELECT timezone, rest_days, rest_days_previous, rest_days_from, chosen_archetype "
                + "FROM v2.users WHERE github_id = ?", USER))
                .containsEntry("timezone", "UTC")
                .containsEntry("rest_days", 0)
                .containsEntry("rest_days_previous", 0)
                .containsEntry("rest_days_from", null)
                .containsEntry("chosen_archetype", null);
    }

    @Test
    void restDaysCheckAcceptsExactlyTheMasksRestDaysAccepts() {
        for (int mask = 0; mask < 128; mask++) {
            boolean valid = Integer.bitCount(mask) <= RestDays.MAX;
            int value = mask;
            if (valid) {
                assertThatCode(() -> setRestDays(value)).as("mask %d", mask).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> setRestDays(value)).as("mask %d", mask)
                        .isInstanceOf(DataIntegrityViolationException.class);
            }
        }
        assertThatThrownBy(() -> setRestDays(128)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> setRestDays(-1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> setRestDays(RestDays.toMask(Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))))
                .doesNotThrowAnyException();
    }

    @Test
    void chosenArchetypeMustBeAClass() {
        assertThatCode(() -> jdbc.update("UPDATE v2.users SET chosen_archetype = 'MONK' WHERE github_id = ?", USER))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> jdbc.update("UPDATE v2.users SET chosen_archetype = 'NECROMANCER' WHERE github_id = ?", USER))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void heroStateRefusesWhatHeroStateRefuses() {
        assertThatCode(() -> insertHero(2, HeroRules.xpForNext(2) - 1, 3)).doesNotThrowAnyException();
        jdbc.update("DELETE FROM v2.hero_state WHERE user_id = ?", USER);

        assertThatThrownBy(() -> insertHero(2, HeroRules.xpForNext(2), 3)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHero(1, -1, 3)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHero(0, 0, 3)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHero(1, 0, 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertHero(1, 0, 4)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anEventIsStoredOncePerTypeAndDay() {
        String insert = "INSERT INTO v2.hero_events (user_id, type, on_date, details) "
                + "VALUES (?, ?, DATE '2026-10-14', '{\"lives\": 2, \"reason\": \"NO_ACTIVITY\"}'::jsonb)";
        jdbc.update(insert, USER, "LIFE_LOST");
        jdbc.update(insert, USER, "LEVEL_DOWN"); // another type, same day: fine

        assertThatThrownBy(() -> jdbc.update(insert, USER, "LIFE_LOST")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, USER, "XP_GAINED")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT details->>'reason' FROM v2.hero_events WHERE user_id = ? AND type = 'LIFE_LOST'",
                String.class, USER)).isEqualTo("NO_ACTIVITY");
    }

    @Test
    void deletingTheUserDeletesTheHero() {
        insertHero(1, 0, 3);
        jdbc.update("INSERT INTO v2.hero_events (user_id, type, on_date) VALUES (?, 'RESTED', DATE '2026-10-14')", USER);
        jdbc.update("INSERT INTO v2.hero_days (user_id, on_date, xp_gained, streak) VALUES (?, DATE '2026-10-14', 0, 0)", USER);

        jdbc.update("DELETE FROM v2.users WHERE github_id = ?", USER);

        for (String table : new String[]{"hero_state", "hero_events", "hero_days"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM v2." + table + " WHERE user_id = ?", Integer.class, USER))
                    .as(table).isZero();
        }
    }

    private void setRestDays(int mask) {
        jdbc.update("UPDATE v2.users SET rest_days = ? WHERE github_id = ?", mask, USER);
    }

    private void insertHero(int level, int xp, int lives) {
        jdbc.update("INSERT INTO v2.hero_state (user_id, level, xp, lives, streak, last_evaluated_on) "
                + "VALUES (?, ?, ?, ?, 0, DATE '2026-10-15')", USER, level, xp, lives);
    }
}
