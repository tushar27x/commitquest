package com.tushar27x.insightshub.it;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.error.TooManyRequestsException;
import com.tushar27x.insightshub.hero.ClassSwitch.Change;
import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;
import com.tushar27x.insightshub.hero.HeroWriter;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.user.HeroClassService;
import com.tushar27x.insightshub.user.HeroResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** {@code PUT /hero/class}'s write on a real Postgres (B6): the JPQL compare-and-set and the event in one transaction. */
@IntegrationTest
class HeroClassIT {

    static final long USER = 9_000_501L; // block 9_000_5xx, see IntegrationTest
    static final Instant T0 = Instant.parse("2026-10-16T12:00:00Z");

    @Autowired
    UsersRepository users;
    @Autowired
    HeroWriter writer;
    @Autowired
    HeroClassService service;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void freshHero() {
        jdbc.update("DELETE FROM v2.users WHERE github_id = ?", USER); // cascades to insights and the hero tables
        jdbc.update("INSERT INTO v2.users (github_id, login, timezone) VALUES (?, 'it-class', 'Asia/Kolkata')", USER);
        jdbc.update("INSERT INTO v2.insights (user_id, archetype, archetype_scores) VALUES (?, 'MONK', CAST(? AS jsonb))",
                USER, """
                        {"PALADIN": 44, "BARBARIAN": 44, "WIZARD": 33, "ARTIFICER": 33, "CLERIC": 0, "BARD": 10,
                         "MONK": 52, "ROGUE": 47}""");
        jdbc.update("INSERT INTO v2.hero_state (user_id, level, xp, lives, streak, last_evaluated_on) "
                + "VALUES (?, 7, 140, 2, 1, ?)", USER, LocalDate.parse("2026-10-15"));
    }

    @Test
    void onlyTheChangeThatReadTheCurrentValueWins() {
        assertThat(users.changeClass(USER, Archetype.ROGUE, T0, null)).isEqualTo(1);
        assertThat(users.changeClass(USER, Archetype.PALADIN, T0.plusSeconds(1), null)).isZero(); // stale: was null

        Instant stored = changedAt();
        assertThat(stored).isEqualTo(T0);
        assertThat(users.changeClass(USER, Archetype.PALADIN, T0.plusSeconds(2), T0.minusSeconds(1))).isZero();
        assertThat(users.changeClass(USER, null, T0.plusSeconds(3), stored)).isEqualTo(1);

        assertThat(jdbc.queryForMap("SELECT chosen_archetype FROM v2.users WHERE github_id = ?", USER))
                .containsEntry("chosen_archetype", null);
        assertThat(changedAt()).isEqualTo(T0.plusSeconds(3));
    }

    @Test
    void theEventIsStoredWithANullChosenAsJson() {
        Map<String, Object> details = new HashMap<>();
        details.put("from", "BARD");
        details.put("to", "MONK");
        details.put("chosen", null);

        assertThat(writer.changeClass(USER, null, new Change(null,
                new HeroEvent(HeroEventType.CLASS_CHANGED, LocalDate.parse("2026-10-16"), details)), T0)).isTrue();

        assertThat(jdbc.queryForObject("SELECT details::text FROM v2.hero_events WHERE user_id = ? AND type = 'CLASS_CHANGED'",
                String.class, USER)).isEqualTo("{\"to\": \"MONK\", \"from\": \"BARD\", \"chosen\": null}");
    }

    @Test
    void aLostCompareAndSetWritesNoEvent() {
        users.changeClass(USER, Archetype.ROGUE, T0, null);

        assertThat(writer.changeClass(USER, null, new Change(Archetype.PALADIN, new HeroEvent(
                HeroEventType.CLASS_CHANGED, LocalDate.parse("2026-10-16"), Map.of("from", "MONK", "to", "PALADIN",
                "chosen", "PALADIN"))), T0.plusSeconds(1))).isFalse();

        assertThat(events()).isZero();
        assertThat(jdbc.queryForObject("SELECT chosen_archetype FROM v2.users WHERE github_id = ?", String.class, USER))
                .isEqualTo("ROGUE");
    }

    @Test
    void theServiceSwitchesThenHoldsTheCooldown() {
        HeroResponse hero = service.change(USER, Archetype.ROGUE).orElseThrow();

        assertThat(hero.heroClass().chosen()).isEqualTo(Archetype.ROGUE);
        assertThat(hero.heroClass().current()).isEqualTo(Archetype.ROGUE);
        assertThat(hero.heroClass().canSwitchAt()).isNotNull();
        assertThat(hero.level()).isEqualTo(7);
        assertThat(events()).isEqualTo(1);

        assertThat(service.change(USER, Archetype.ROGUE)).isPresent(); // the same again: no-op
        assertThatThrownBy(() -> service.change(USER, Archetype.PALADIN)).isInstanceOf(TooManyRequestsException.class);
        assertThatThrownBy(() -> service.change(USER, Archetype.WIZARD)).isInstanceOf(ResponseStatusException.class);
        assertThat(events()).isEqualTo(1);
        assertThat(changedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void noHeroIsEmpty() {
        jdbc.update("DELETE FROM v2.hero_state WHERE user_id = ?", USER);

        assertThat(service.change(USER, Archetype.ROGUE)).isEmpty();
    }

    private Instant changedAt() {
        return jdbc.queryForObject("SELECT archetype_changed_at FROM v2.users WHERE github_id = ?",
                OffsetDateTime.class, USER).toInstant();
    }

    private int events() {
        return jdbc.queryForObject("SELECT count(*) FROM v2.hero_events WHERE user_id = ?", Integer.class, USER);
    }
}
