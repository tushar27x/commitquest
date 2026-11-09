package com.tushar27x.insightshub.it;

import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiTestData;
import com.tushar27x.insightshub.ai.CharacterSheet;
import com.tushar27x.insightshub.auth.AuthProperties;
import com.tushar27x.insightshub.auth.AuthService;
import com.tushar27x.insightshub.auth.GitHubOAuthClient;
import com.tushar27x.insightshub.auth.GitHubOAuthClient.GitHubAccount;
import com.tushar27x.insightshub.auth.GitHubOAuthClient.OAuthToken;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.JwtService;
import com.tushar27x.insightshub.security.TokenCipher;
import com.tushar27x.insightshub.sync.SyncService;
import com.tushar27x.insightshub.sync.SyncService.PreparedResync;
import com.tushar27x.insightshub.user.Preferences;
import com.tushar27x.insightshub.user.PreferencesWriter;
import com.tushar27x.insightshub.user.PrivateProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B11 on a real Postgres: V10's columns, the compare-and-set on the switch, one transaction for the switch, the lock
 * and the resync's writes (a failure rolls all of it back), the reset of branch counts, and the callback's flags
 * surviving a save. The GitHub calls and the LLM are the unit tests' business: a {@link PreparedResync} is built by hand.
 */
@IntegrationTest
class PrivateLockIT {

    static final long USER = 9_001_101L; // block 9_001_1xx, see IntegrationTest
    static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    static final LocalDate TODAY = LocalDate.parse("2026-11-09");
    static final String WITH_REPO = "read:user,user:email,repo";

    @Autowired
    UsersRepository users;
    @Autowired
    PreferencesWriter preferencesWriter;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    TokenCipher cipher;
    @Autowired
    JwtService jwtService;
    @Autowired
    AuthProperties authProperties;
    @Autowired
    PrivateProperties privateProperties;

    @BeforeEach
    void freshUser() {
        jdbc.update("DELETE FROM v2.users WHERE github_id BETWEEN 9001100 AND 9001199");
        jdbc.update("INSERT INTO v2.users (github_id, login, github_scopes, include_private) VALUES (?, 'it-lock', ?, false)",
                USER, WITH_REPO);
    }

    // --- V10 ---

    @Test
    void v10AddsTheLockColumnNullAndTheResyncFlagFalse() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT private_changed_at, private_resync_due FROM v2.users WHERE github_id = ?", USER);

        assertThat(row.get("private_changed_at")).isNull();
        assertThat(row.get("private_resync_due")).isEqualTo(false);
    }

    @Test
    void theLockAndTheFlagRoundTripThroughTheEntity() {
        var user = users.findById(USER).orElseThrow();
        user.setPrivateChangedAt(NOW);
        user.setPrivateResyncDue(true);
        users.save(user);

        var read = users.findById(USER).orElseThrow();
        assertThat(read.getPrivateChangedAt()).isEqualTo(NOW);
        assertThat(read.isPrivateResyncDue()).isTrue();
        assertThat(users.setPrivateResyncDue(USER, false)).isEqualTo(1);
        assertThat(users.findById(USER).orElseThrow().isPrivateResyncDue()).isFalse();
    }

    // --- the compare-and-set ---

    @Test
    void theSwitchIsStoredOnlyWhileItStillHasTheValueAndLockTheDecisionReadTheFirstTime() {
        assertThat(inTransaction(() -> users.changeIncludePrivate(USER, false, true, null))).isEqualTo(1);
        // a stale decision (still thinks it is false, never changed) loses
        assertThat(inTransaction(() -> users.changeIncludePrivate(USER, false, true, null))).isZero();
        assertThat(inTransaction(() -> users.changeIncludePrivate(USER, true, false, null))).isEqualTo(1);
    }

    @Test
    void afterAChangeTheCompareIsOnTheStoredInstant() {
        jdbc.update("UPDATE v2.users SET private_changed_at = ? WHERE github_id = ?", Timestamp.from(NOW), USER);

        assertThat(inTransaction(() -> users.changeIncludePrivate(USER, false, true, NOW.minusSeconds(1)))).isZero();
        assertThat(inTransaction(() -> users.changeIncludePrivate(USER, false, true, null))).isZero(); // "never" is stale
        assertThat(inTransaction(() -> users.changeIncludePrivate(USER, false, true, NOW))).isEqualTo(1);
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new org.springframework.transaction.support.TransactionTemplate(
                transactionManager).execute(status -> work.get());
    }

    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactionManager;

    // --- one transaction for the switch, the fields, the lock and the resync ---

    private PreparedResync resync(boolean includePrivate, Map<LocalDate, Integer> branchCounts) {
        var result = AiTestData.insights();
        Map<String, Object> stats = jsonMapper.convertValue(result.stats(), new TypeReference<>() {
        });
        var texts = new AiInsights("year", "week", "craft", Map.of(), new CharacterSheet("title", "lore"));
        return new PreparedResync(USER, includePrivate, false,
                List.of(new ContributionDay(TODAY.minusDays(1), 3), new ContributionDay(TODAY, 0)), branchCounts, stats,
                result, texts, NOW);
    }

    private static Preferences prefs(String zone, boolean includePrivate) {
        return new Preferences(zone, List.of(), true, false, includePrivate, null);
    }

    private void seedActivity() {
        jdbc.update("INSERT INTO v2.daily_contributions (user_id, date, calendar_count, branch_commit_count) VALUES "
                + "(?, ?, 9, 0), (?, ?, 0, 5), (?, ?, 0, 4)",
                USER, TODAY.minusDays(1), USER, TODAY.minusDays(30), USER, TODAY);
    }

    private Map<LocalDate, Integer> branchCounts() {
        Map<LocalDate, Integer> counts = new java.util.TreeMap<>();
        jdbc.query("SELECT date, branch_commit_count FROM v2.daily_contributions WHERE user_id = ?", rs -> {
            counts.put(rs.getObject("date", LocalDate.class), rs.getInt("branch_commit_count"));
        }, USER);
        return counts;
    }

    @Test
    void anEffectiveChangeCommitsTheSwitchTheFieldsTheLockTheInsightsAndTheActivityTogether() {
        seedActivity();

        Optional<Preferences> saved = preferencesWriter.commitSwitch(USER, prefs("Asia/Kolkata", true), false, null,
                true, resync(true, Map.of(TODAY, 2)), NOW);

        assertThat(saved).isPresent();
        Map<String, Object> row = jdbc.queryForMap("SELECT include_private, timezone, private_changed_at, "
                + "private_resync_due, last_synced_at FROM v2.users WHERE github_id = ?", USER);
        assertThat(row).containsEntry("include_private", true).containsEntry("timezone", "Asia/Kolkata");
        assertThat(((Timestamp) row.get("private_changed_at")).toInstant()).isEqualTo(NOW);
        assertThat(row.get("last_synced_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM v2.insights WHERE user_id = ?", Integer.class, USER))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT calendar_count FROM v2.daily_contributions WHERE user_id = ? AND date = ?",
                Integer.class, USER, TODAY.minusDays(1))).isEqualTo(3); // the calendar was rewritten
        // turning ON: the scan replaces its own days, and the other days keep their counts
        assertThat(branchCounts()).containsEntry(TODAY, 2).containsEntry(TODAY.minusDays(30), 5);
    }

    @Test
    void turningOffClearsEveryBranchCountThePrivateBranchesAdded() {
        seedActivity();
        jdbc.update("UPDATE v2.users SET include_private = true WHERE github_id = ?", USER);

        preferencesWriter.commitSwitch(USER, prefs("UTC", false), true, null, true,
                resync(false, Map.of(TODAY, 1)), NOW); // the public scan finds 1 commit today

        // not GREATEST: today's 4 became 1, and the old 5 on another day is gone
        assertThat(branchCounts()).containsEntry(TODAY, 1).containsEntry(TODAY.minusDays(30), 0)
                .containsEntry(TODAY.minusDays(1), 0);
    }

    @Test
    void aFailedResyncCommitsNothing() {
        seedActivity();
        PreparedResync broken = new PreparedResync(USER, true, false,
                List.of(new ContributionDay(TODAY, 7)), Map.of(TODAY, 8), Map.of(), null /* the engine result */,
                null, NOW);

        assertThatThrownBy(() -> preferencesWriter.commitSwitch(USER, prefs("Asia/Kolkata", true), false, null, true,
                broken, NOW)).isInstanceOf(RuntimeException.class);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT include_private, timezone, private_changed_at FROM v2.users WHERE github_id = ?", USER);
        assertThat(row).containsEntry("include_private", false).containsEntry("timezone", "UTC");
        assertThat(row.get("private_changed_at")).isNull(); // no flip, no lock, no other field
        assertThat(jdbc.queryForObject("SELECT count(*) FROM v2.insights WHERE user_id = ?", Integer.class, USER)).isZero();
        assertThat(branchCounts()).containsEntry(TODAY, 4).containsEntry(TODAY.minusDays(30), 5); // activity untouched
        assertThat(jdbc.queryForObject("SELECT calendar_count FROM v2.daily_contributions WHERE user_id = ? AND date = ?",
                Integer.class, USER, TODAY)).isZero();
    }

    @Test
    void aLostRaceWritesNothing() {
        seedActivity();
        jdbc.update("UPDATE v2.users SET include_private = true, private_changed_at = ? WHERE github_id = ?",
                Timestamp.from(NOW.minusSeconds(5)), USER); // another request flipped it first

        Optional<Preferences> saved = preferencesWriter.commitSwitch(USER, prefs("Asia/Kolkata", true), false, null,
                true, resync(true, Map.of(TODAY, 2)), NOW);

        assertThat(saved).isEmpty();
        assertThat(jdbc.queryForObject("SELECT timezone FROM v2.users WHERE github_id = ?", String.class, USER))
                .isEqualTo("UTC");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM v2.insights WHERE user_id = ?", Integer.class, USER)).isZero();
        assertThat(branchCounts()).containsEntry(TODAY, 4);
    }

    @Test
    void anUnlockedCommitStoresTheWishWithoutStartingTheLockAndPaysAnOwedResync() {
        jdbc.update("UPDATE v2.users SET private_resync_due = true WHERE github_id = ?", USER);

        Optional<Preferences> saved = preferencesWriter.commitSwitch(USER, prefs("UTC", true), false, null,
                false, resync(false, Map.of()), NOW);

        assertThat(saved.orElseThrow().privateLockedUntil()).isNull();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT private_changed_at, private_resync_due FROM v2.users WHERE github_id = ?", USER);
        assertThat(row.get("private_changed_at")).isNull();
        assertThat(row.get("private_resync_due")).isEqualTo(false);
    }

    @Test
    void theLockEndIsServedFromTheStoredInstant() {
        jdbc.update("UPDATE v2.users SET private_changed_at = ? WHERE github_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS)), USER);

        Preferences read = preferencesReader().get(USER).orElseThrow();

        assertThat(read.privateLockedUntil()).isAfter(Instant.now().plus(Duration.ofDays(5)));
    }

    @Autowired
    com.tushar27x.insightshub.user.PreferencesService preferencesService;

    private com.tushar27x.insightshub.user.PreferencesService preferencesReader() {
        return preferencesService;
    }

    // --- the callback: grant -> lock -> the second private intent is ignored ---

    @Test
    void grantLocksAndASecondPrivateIntentIsIgnoredWhileLocked() {
        GitHubOAuthClient github = mock(GitHubOAuthClient.class);
        SyncService sync = mock(SyncService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AuthService auth = new AuthService(github, users, cipher, jwtService, sync, authProperties, privateProperties, clock);
        // an unrelated token-less row: the callback upserts the user by GitHub id
        jdbc.update("DELETE FROM v2.users WHERE github_id = ?", USER);
        String access = "access-" + "value"; // not token-shaped (B31)
        when(github.exchangeCode("code", "http://cb")).thenReturn(new OAuthToken(access, "read:user,user:email,repo"));
        when(github.fetchAccount(access)).thenReturn(new GitHubAccount(USER, "it-lock"));
        when(github.fetchPrimaryEmail(access)).thenReturn("it-lock@example.com");

        auth.completeLogin("code", "http://cb", true);

        Map<String, Object> first = jdbc.queryForMap("SELECT include_private, private_changed_at, private_resync_due, "
                + "github_scopes FROM v2.users WHERE github_id = ?", USER);
        assertThat(first).containsEntry("include_private", true).containsEntry("github_scopes", WITH_REPO);
        assertThat(((Timestamp) first.get("private_changed_at")).toInstant()).isEqualTo(NOW);
        assertThat(first.get("private_resync_due")).isEqualTo(true);
        verify(sync).sync(USER);

        // the user turns it off in a later request, outside the lock's reach (data setup), then asks again within the lock
        jdbc.update("UPDATE v2.users SET include_private = false WHERE github_id = ?", USER);
        AuthService later = new AuthService(github, users, cipher, jwtService, mock(SyncService.class), authProperties,
                privateProperties, Clock.fixed(NOW.plus(Duration.ofDays(2)), ZoneOffset.UTC));
        later.completeLogin("code", "http://cb", true);

        Map<String, Object> second = jdbc.queryForMap(
                "SELECT include_private, private_changed_at, github_scopes FROM v2.users WHERE github_id = ?", USER);
        assertThat(second).containsEntry("include_private", false).containsEntry("github_scopes", WITH_REPO);
        assertThat(((Timestamp) second.get("private_changed_at")).toInstant()).isEqualTo(NOW); // not restarted
        verify(sync, never()).syncIfDue(anyLong());
    }
}
