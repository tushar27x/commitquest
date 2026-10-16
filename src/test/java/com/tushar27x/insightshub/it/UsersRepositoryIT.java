package com.tushar27x.insightshub.it;

import com.tushar27x.insightshub.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The atomic sync claim (Phase 7) and the daily sync's user query (B3) against a real database. */
@IntegrationTest
class UsersRepositoryIT {

    static final long USER = 9_000_101L; // block 9_000_1xx, see IntegrationTest
    static final Duration COOLDOWN = Duration.ofMinutes(10);

    @Autowired
    UsersRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void freshUser() {
        jdbc.update("DELETE FROM v2.users WHERE github_id = ?", USER);
        jdbc.update("INSERT INTO v2.users (github_id, login) VALUES (?, 'it-claim')", USER);
    }

    @Test
    void onlyTheFirstClaimInsideTheCooldownWins() {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");

        assertThat(users.claimSync(USER, now, now.minus(COOLDOWN))).isEqualTo(1);
        Instant soon = now.plusSeconds(60);
        assertThat(users.claimSync(USER, soon, soon.minus(COOLDOWN))).isZero();

        Instant later = now.plus(COOLDOWN).plusSeconds(1);
        assertThat(users.claimSync(USER, later, later.minus(COOLDOWN))).isEqualTo(1);
    }

    @Test
    void scheduledSyncTakesOnlyUsersWithAUsableToken() { // B3
        long noToken = USER + 10;
        long rejected = USER + 11;
        jdbc.update("DELETE FROM v2.users WHERE github_id IN (?, ?)", noToken, rejected);
        jdbc.update("UPDATE v2.users SET encrypted_github_token = 'sealed' WHERE github_id = ?", USER);
        jdbc.update("INSERT INTO v2.users (github_id, login) VALUES (?, 'it-no-token')", noToken);
        jdbc.update("INSERT INTO v2.users (github_id, login, encrypted_github_token, needs_reauth) "
                + "VALUES (?, 'it-rejected', 'sealed', true)", rejected);

        assertThat(users.findIdsForScheduledSync()).contains(USER).doesNotContain(noToken, rejected);
    }

    @Test
    void anUnknownUserClaimsNothing() {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        assertThat(users.claimSync(USER + 1, now, now.minus(COOLDOWN))).isZero();
    }
}
