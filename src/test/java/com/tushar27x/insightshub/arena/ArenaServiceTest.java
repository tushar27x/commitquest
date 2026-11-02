package com.tushar27x.insightshub.arena;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.hero.Tier;
import com.tushar27x.insightshub.repository.ArenaRepository;
import com.tushar27x.insightshub.repository.ArenaRepository.Rank;
import com.tushar27x.insightshub.repository.ArenaRepository.Row;
import com.tushar27x.insightshub.user.Fixtures;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArenaServiceTest {

    private static final long ME = 583231L;
    private static final long OTHER = 1_000_001L;

    private final ArenaRepository repository = mock(ArenaRepository.class);
    private final ArenaService service = new ArenaService(repository);
    private final JsonMapper json = JsonMapper.builder().build();

    /** The fixture's board: the hero the other fixtures describe is GitHub id 9_100_004, rank 4 of 6. */
    private static final long FIXTURE_ME = 9_100_004L;

    @Test
    void theFixtureBoardReplayedThroughTheServiceIsExactlyLeaderboardJson() {
        when(repository.currentClass(FIXTURE_ME)).thenReturn(Optional.of(Archetype.MONK));
        when(repository.count(Archetype.MONK)).thenReturn(6);
        when(repository.page(Archetype.MONK, Arena.DEFAULT_PAGE_SIZE, 0L)).thenReturn(List.of(
                new Row(9_100_001L, "ironvow", 14, 500, 3, 1),
                new Row(9_100_002L, "quietfist", 9, 300, 3, 2),
                new Row(9_100_003L, "stillwater", 9, 300, 2, 2),
                new Row(FIXTURE_ME, "tushar27x", 7, 140, 2, 4),
                new Row(9_100_005L, "dawnkick", 4, 120, 1, 5),
                new Row(9_100_006L, "novice-monk", 1, 10, 3, 6)));

        LeaderboardResponse expected = json.readValue(Fixtures.read("leaderboard.json"), LeaderboardResponse.class);

        // tiers, avatar URLs and is_you are the service's work: the repository rows carry none of them
        assertThat(service.leaderboard(FIXTURE_ME, null, 0, Arena.DEFAULT_PAGE_SIZE)).contains(expected);
    }

    @Test
    void theFixtureRankReplayedThroughTheServiceIsExactlyRankJson() {
        when(repository.currentClass(FIXTURE_ME)).thenReturn(Optional.of(Archetype.MONK));
        when(repository.rankOf(FIXTURE_ME, Archetype.MONK)).thenReturn(Optional.of(new Rank(4, 6)));

        assertThat(service.rank(FIXTURE_ME)).contains(json.readValue(Fixtures.read("rank.json"), RankResponse.class));
    }

    @Test
    void withoutAClassTheBoardIsTheCallersCurrentClass() {
        when(repository.currentClass(ME)).thenReturn(Optional.of(Archetype.MONK));
        when(repository.count(Archetype.MONK)).thenReturn(3);
        when(repository.page(Archetype.MONK, 20, 0L)).thenReturn(List.of(
                new Row(OTHER, "other", 12, 300, 3, 1),
                new Row(ME, "octocat", 7, 140, 2, 2)));

        LeaderboardResponse board = service.leaderboard(ME, null, 0, 20).orElseThrow();

        assertThat(board.archetype()).isEqualTo(Archetype.MONK);
        assertThat(board.total()).isEqualTo(3);
        assertThat(board.entries()).containsExactly(
                new LeaderboardResponse.Entry(1, "other", Arena.avatarUrl(OTHER), 12, 300, 3, Tier.KEEP, false),
                new LeaderboardResponse.Entry(2, "octocat", Arena.avatarUrl(ME), 7, 140, 2, Tier.VILLAGE, true));
    }

    @Test
    void anyClassCanBeReadEvenBeforeTheFirstSync() {
        when(repository.page(Archetype.ROGUE, 20, 40L)).thenReturn(List.of());

        LeaderboardResponse board = service.leaderboard(ME, Archetype.ROGUE, 2, 20).orElseThrow();

        assertThat(board.archetype()).isEqualTo(Archetype.ROGUE);
        assertThat(board.page()).isEqualTo(2);
        assertThat(board.entries()).isEmpty();
        verify(repository, never()).currentClass(anyLong());
    }

    @Test
    void withoutAClassOrAHeroThereIsNoBoard() {
        when(repository.currentClass(ME)).thenReturn(Optional.empty());

        assertThat(service.leaderboard(ME, null, 0, 20)).isEmpty();
        verify(repository, never()).page(eq(Archetype.MONK), anyInt(), anyLong());
    }

    @Test
    void theOffsetDoesNotOverflowOnAHugePage() {
        service.leaderboard(ME, Archetype.BARD, Integer.MAX_VALUE, 50);

        verify(repository).page(Archetype.BARD, 50, (long) Integer.MAX_VALUE * 50);
    }

    @Test
    void aRankedCallerGetsTheirRankAndTopPercent() {
        when(repository.currentClass(ME)).thenReturn(Optional.of(Archetype.MONK));
        when(repository.rankOf(ME, Archetype.MONK)).thenReturn(Optional.of(new Rank(4, 37)));

        assertThat(service.rank(ME)).contains(new RankResponse(Archetype.MONK, 4, 37, 11));
    }

    @Test
    void aCallerOffTheBoardIsNotInTheArena() {
        // private, or needs_reauth: the query leaves both off the board, so the service sees the same thing
        when(repository.currentClass(ME)).thenReturn(Optional.of(Archetype.MONK));
        when(repository.rankOf(ME, Archetype.MONK)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rank(ME)).isInstanceOf(NotInArenaException.class);
    }

    @Test
    void noRankBeforeTheFirstSync() {
        when(repository.currentClass(ME)).thenReturn(Optional.empty());

        assertThat(service.rank(ME)).isEmpty();
        verify(repository, never()).rankOf(anyLong(), any());
    }
}
