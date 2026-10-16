package com.tushar27x.insightshub.it;

import com.tushar27x.insightshub.repository.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The atomic sync claim (Phase 7) needs a real database to test: PROGRESS.md left it for Phase 14. */
@IntegrationTest
class UsersRepositoryIT {

    static final long USER = 9_000_002L;
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
    void anUnknownUserClaimsNothing() {
        Instant now = Instant.parse("2026-10-16T12:00:00Z");
        assertThat(users.claimSync(USER + 1, now, now.minus(COOLDOWN))).isZero();
    }
}
