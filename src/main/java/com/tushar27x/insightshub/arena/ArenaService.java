package com.tushar27x.insightshub.arena;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.hero.HeroView;
import com.tushar27x.insightshub.repository.ArenaRepository;
import com.tushar27x.insightshub.repository.ArenaRepository.Rank;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The Arena's read side (Phase 13): a class's leaderboard and the caller's rank on their own class's board.
 * Ranking is by hero level, then XP, within the current class, for public profiles ({@link ArenaRepository}).
 */
@Service
public class ArenaService {

    private final ArenaRepository arena;

    public ArenaService(ArenaRepository arena) {
        this.arena = arena;
    }

    /**
     * One page of a board. {@code archetype} null means the caller's current class; empty when it's null and the
     * caller has no hero yet. Any caller may read any class's board, public or not.
     */
    @Transactional(readOnly = true)
    public Optional<LeaderboardResponse> leaderboard(long callerId, Archetype archetype, int page, int size) {
        Optional<Archetype> board = archetype != null
                ? Optional.of(archetype)
                : arena.currentClass(callerId);
        return board.map(chosen -> new LeaderboardResponse(chosen, page, size, arena.count(chosen),
                arena.page(chosen, size, (long) page * size).stream()
                        .map(row -> new LeaderboardResponse.Entry(row.rank(), row.login(), Arena.avatarUrl(row.userId()),
                                row.level(), row.xp(), row.lives(), HeroView.tier(row.level()), row.userId() == callerId))
                        .toList()));
    }

    /**
     * The caller's rank on the board of their current class. Empty before the first sync.
     *
     * @throws NotInArenaException the caller has a hero but isn't on the board (private, or {@code needs_reauth})
     */
    @Transactional(readOnly = true)
    public Optional<RankResponse> rank(long callerId) {
        return arena.currentClass(callerId).map(archetype -> {
            Rank mine = arena.rankOf(callerId, archetype).orElseThrow(NotInArenaException::new);
            return new RankResponse(archetype, mine.rank(), mine.total(), Arena.topPercent(mine.rank(), mine.total()));
        });
    }
}
