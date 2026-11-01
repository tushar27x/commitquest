package com.tushar27x.insightshub.arena;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.hero.Tier;
import com.tushar27x.insightshub.repository.ArenaRepository;
import com.tushar27x.insightshub.repository.ArenaRepository.Rank;
import com.tushar27x.insightshub.repository.ArenaRepository.Row;
import com.tushar27x.insightshub.repository.ArenaRepository.Standing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void withoutAClassTheBoardIsTheCallersCurrentClass() {
        when(repository.standing(ME)).thenReturn(Optional.of(new Standing(true, false, Archetype.MONK)));
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
        verify(repository, never()).standing(anyLong());
    }

    @Test
    void withoutAClassOrAHeroThereIsNoBoard() {
        when(repository.standing(ME)).thenReturn(Optional.empty());

        assertThat(service.leaderboard(ME, null, 0, 20)).isEmpty();
        verify(repository, never()).page(eq(Archetype.MONK), anyInt(), anyLong());
    }

    @Test
    void theOffsetDoesNotOverflowOnAHugePage() {
        service.leaderboard(ME, Archetype.BARD, Integer.MAX_VALUE, 50);

        verify(repository).page(Archetype.BARD, 50, (long) Integer.MAX_VALUE * 50);
    }

    @Test
    void aPublicCallerGetsTheirRankAndTopPercent() {
        when(repository.standing(ME)).thenReturn(Optional.of(new Standing(true, false, Archetype.MONK)));
        when(repository.rankOf(ME, Archetype.MONK)).thenReturn(Optional.of(new Rank(4, 37)));

        assertThat(service.rank(ME)).contains(new RankResponse(Archetype.MONK, true, 4, 37, 11));
    }

    @Test
    void aPrivateCallerIsNotRankedButSeesTheBoardSize() {
        when(repository.standing(ME)).thenReturn(Optional.of(new Standing(false, false, Archetype.MONK)));
        when(repository.rankOf(ME, Archetype.MONK)).thenReturn(Optional.empty());
        when(repository.count(Archetype.MONK)).thenReturn(36);

        assertThat(service.rank(ME)).contains(new RankResponse(Archetype.MONK, false, null, 36, null));
    }

    @Test
    void aRevokedTokenTakesAPublicCallerOffTheBoard() {
        when(repository.standing(ME)).thenReturn(Optional.of(new Standing(true, true, Archetype.WIZARD)));
        when(repository.rankOf(ME, Archetype.WIZARD)).thenReturn(Optional.empty());
        when(repository.count(Archetype.WIZARD)).thenReturn(5);

        assertThat(service.rank(ME)).contains(new RankResponse(Archetype.WIZARD, true, null, 5, null));
    }

    @Test
    void noRankBeforeTheFirstSync() {
        when(repository.standing(ME)).thenReturn(Optional.empty());

        assertThat(service.rank(ME)).isEmpty();
    }
}
