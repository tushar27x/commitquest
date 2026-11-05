package com.tushar27x.insightshub.it;

import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.sync.InsightsWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B12 on a real Postgres: V8's {@code github_token_obtained_at}, the compare-and-set that flags {@code needs_reauth}
 * only for the token GitHub rejected, and {@code @DynamicUpdate} keeping a login's new token when a short transaction
 * that read the old one commits after it (B45). The "tokens" are placeholder ciphertext strings, never token-shaped.
 */
@IntegrationTest
class GitHubTokenIT {

    static final long USER = 9_001_001L; // block 9_001_0xx, see IntegrationTest
    static final Instant LOGIN_AT = Instant.parse("2026-11-05T02:30:00Z");

    @Autowired
    UsersRepository users;
    @Autowired
    InsightsWriter writer;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DataSource dataSource;
    @Autowired
    TransactionTemplate transactions;

    @BeforeEach
    void freshUser() {
        jdbc.update("DELETE FROM v2.users WHERE github_id BETWEEN 9001000 AND 9001099");
        jdbc.update("INSERT INTO v2.users (github_id, login, encrypted_github_token) VALUES (?, 'it-token', 'cipher-A')",
                USER);
    }

    @Test
    void aLoginsTokenTimeIsStored() {
        Users user = users.findById(USER).orElseThrow();
        user.replaceGithubToken("cipher-B", LOGIN_AT);
        users.save(user);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT encrypted_github_token, github_token_obtained_at FROM v2.users WHERE github_id = ?", USER);
        assertThat(row).containsEntry("encrypted_github_token", "cipher-B");
        assertThat(((java.sql.Timestamp) row.get("github_token_obtained_at")).toInstant()).isEqualTo(LOGIN_AT);
    }

    @Test
    void onlyTheRejectedTokenIsFlagged() {
        assertThat(writer.markNeedsReauth(USER, "cipher-old")).isFalse(); // a login replaced it meanwhile
        assertThat(needsReauth()).isFalse();

        assertThat(writer.markNeedsReauth(USER, "cipher-A")).isTrue();
        assertThat(needsReauth()).isTrue();
    }

    @Test
    void aShortTransactionThatReadTheOldTokenDoesNotWriteItBack() {
        transactions.executeWithoutResult(status -> {
            Users user = users.findById(USER).orElseThrow(); // reads cipher-A
            loginOnAnotherConnection("cipher-B");             // a login commits its new token meanwhile
            user.setLastSyncedAt(LOGIN_AT.plus(1, ChronoUnit.MINUTES)); // what InsightsWriter does
        });

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT encrypted_github_token, last_synced_at FROM v2.users WHERE github_id = ?", USER);
        assertThat(row).containsEntry("encrypted_github_token", "cipher-B");
        assertThat(row.get("last_synced_at")).isNotNull();
    }

    private void loginOnAnotherConnection(String newToken) {
        try (Connection connection = dataSource.getConnection(); // not the transaction's connection
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE v2.users SET encrypted_github_token = ? WHERE github_id = ?")) {
            connection.setAutoCommit(true);
            update.setString(1, newToken);
            update.setLong(2, USER);
            update.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean needsReauth() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT needs_reauth FROM v2.users WHERE github_id = ?",
                Boolean.class, USER));
    }
}
