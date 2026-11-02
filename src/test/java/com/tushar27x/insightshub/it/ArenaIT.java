package com.tushar27x.insightshub.it;

import com.tushar27x.insightshub.arena.ArenaService;
import com.tushar27x.insightshub.arena.LeaderboardResponse;
import com.tushar27x.insightshub.arena.NotInArenaException;
import com.tushar27x.insightshub.arena.RankResponse;
import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.repository.ArenaRepository;
import com.tushar27x.insightshub.repository.ArenaRepository.Rank;
import com.tushar27x.insightshub.repository.ArenaRepository.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Arena's window query on a real Postgres (B8). The board is global and the database is shared with the other
 * ITs, so these heroes sit at levels 900+ (above any other IT's) and the totals are compared with {@code count}.
 */
@IntegrationTest
class ArenaIT {

    static final long BASE = 9_000_700L; // block 9_000_7xx, see IntegrationTest
    static final long TIED_LOW_ID = BASE + 1;
    static final long TIED_CHOSEN = BASE + 2;   // scored ROGUE, chose MONK: on the MONK board
    static final long THIRD = BASE + 3;
    static final long PRIVATE = BASE + 4;
    static final long REAUTH = BASE + 5;
    static final long MOVED_ON = BASE + 6;      // scored MONK, chose WIZARD: not on the MONK board
    static final long NO_HERO = BASE + 7;

    @Autowired
    ArenaRepository arena;
    @Autowired
    ArenaService service;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void board() {
        jdbc.update("DELETE FROM v2.users WHERE github_id BETWEEN ? AND ?", BASE, BASE + 99); // cascades
        hero(TIED_LOW_ID, "it-tied-a", true, false, "MONK", null, 900, 10);
        hero(TIED_CHOSEN, "it-tied-b", true, false, "ROGUE", "MONK", 900, 10);
        hero(THIRD, "it-third", true, false, "MONK", null, 899, 44_000);
        hero(PRIVATE, "it-private", false, false, "MONK", null, 950, 0);
        hero(REAUTH, "it-reauth", true, true, "MONK", null, 960, 0);
        hero(MOVED_ON, "it-moved-on", true, false, "MONK", "WIZARD", 970, 0);
        user(NO_HERO, "it-no-hero", true, false, "MONK", null);
    }

    @Test
    void levelThenXpWithTiesSharingARankAndListedByGitHubId() {
        assertThat(arena.page(Archetype.MONK, 3, 0)).containsExactly(
                new Row(TIED_LOW_ID, "it-tied-a", 900, 10, 3, 1),
                new Row(TIED_CHOSEN, "it-tied-b", 900, 10, 3, 1),
                new Row(THIRD, "it-third", 899, 44_000, 3, 3));
    }

    @Test
    void pagesFollowOnWithoutOverlap() {
        assertThat(arena.page(Archetype.MONK, 2, 2)).first()
                .isEqualTo(new Row(THIRD, "it-third", 899, 44_000, 3, 3));
        assertThat(arena.page(Archetype.MONK, 20, 1_000_000)).isEmpty();
    }

    @Test
    void privateRevokedOtherClassAndHerolessUsersAreNotOnTheBoard() {
        List<Long> onBoard = arena.page(Archetype.MONK, 1000, 0).stream().map(Row::userId).toList();

        assertThat(onBoard).contains(TIED_LOW_ID, TIED_CHOSEN, THIRD).doesNotContain(PRIVATE, REAUTH, MOVED_ON, NO_HERO);
        assertThat(onBoard).hasSize(arena.count(Archetype.MONK));
        assertThat(arena.page(Archetype.WIZARD, 1, 0).getFirst().userId()).isEqualTo(MOVED_ON);
        assertThat(arena.page(Archetype.ROGUE, 1000, 0)).extracting(Row::userId).doesNotContain(TIED_CHOSEN);
    }

    @Test
    void theRankAgreesWithTheBoard() {
        assertThat(arena.rankOf(THIRD, Archetype.MONK)).contains(new Rank(3, arena.count(Archetype.MONK)));
        assertThat(arena.rankOf(PRIVATE, Archetype.MONK)).isEmpty();
        assertThat(arena.rankOf(REAUTH, Archetype.MONK)).isEmpty();
    }

    @Test
    void theCurrentClassIsKnownWhateverTheVisibility() {
        assertThat(arena.currentClass(TIED_CHOSEN)).contains(Archetype.MONK);
        assertThat(arena.currentClass(MOVED_ON)).contains(Archetype.WIZARD);
        assertThat(arena.currentClass(PRIVATE)).contains(Archetype.MONK);
        assertThat(arena.currentClass(REAUTH)).contains(Archetype.MONK);
        assertThat(arena.currentClass(NO_HERO)).isEmpty();
    }

    @Test
    void theServiceEndToEnd() {
        LeaderboardResponse board = service.leaderboard(TIED_CHOSEN, null, 0, 3).orElseThrow();
        assertThat(board.archetype()).isEqualTo(Archetype.MONK);
        assertThat(board.entries()).extracting(LeaderboardResponse.Entry::isYou).containsExactly(false, true, false);

        int total = arena.count(Archetype.MONK);
        assertThat(service.rank(TIED_LOW_ID)).contains(
                new RankResponse(Archetype.MONK, 1, total, (int) Math.max(1, (100L + total - 1) / total)));
        assertThat(service.rank(NO_HERO)).isEmpty();
    }

    @Test
    void privateAndRevokedCallersAreNotInTheArena() {
        assertThatThrownBy(() -> service.rank(PRIVATE)).isInstanceOf(NotInArenaException.class);
        assertThatThrownBy(() -> service.rank(REAUTH)).isInstanceOf(NotInArenaException.class);
    }

    private void hero(long id, String login, boolean isPublic, boolean needsReauth, String scored, String chosen,
                      int level, int xp) {
        user(id, login, isPublic, needsReauth, scored, chosen);
        jdbc.update("INSERT INTO v2.hero_state (user_id, level, xp, lives, streak, last_evaluated_on) "
                + "VALUES (?, ?, ?, 3, 0, ?)", id, level, xp, LocalDate.parse("2026-10-31"));
    }

    private void user(long id, String login, boolean isPublic, boolean needsReauth, String scored, String chosen) {
        jdbc.update("INSERT INTO v2.users (github_id, login, public_profile, needs_reauth, chosen_archetype) "
                + "VALUES (?, ?, ?, ?, ?)", id, login, isPublic, needsReauth, chosen);
        jdbc.update("INSERT INTO v2.insights (user_id, archetype) VALUES (?, ?)", id, scored);
    }
}
