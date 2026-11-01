package com.tushar27x.insightshub.arena;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.hero.HeroView;
import com.tushar27x.insightshub.repository.ArenaRepository;
import com.tushar27x.insightshub.repository.ArenaRepository.Rank;
import com.tushar27x.insightshub.repository.ArenaRepository.Standing;
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
                : arena.standing(callerId).map(Standing::archetype);
        return board.map(chosen -> new LeaderboardResponse(chosen, page, size, arena.count(chosen),
                arena.page(chosen, size, (long) page * size).stream()
                        .map(row -> new LeaderboardResponse.Entry(row.rank(), row.login(), Arena.avatarUrl(row.userId()),
                                row.level(), row.xp(), row.lives(), HeroView.tier(row.level()), row.userId() == callerId))
                        .toList()));
    }

    /**
     * The caller's rank on the board of their current class. Empty before the first sync, and for a private
     * profile unless {@link Arena#RANK_200_WHEN_PRIVATE}.
     */
    @Transactional(readOnly = true)
    public Optional<RankResponse> rank(long callerId) {
        Optional<Standing> standing = arena.standing(callerId);
        if (standing.isEmpty() || (!standing.get().publicProfile() && !Arena.RANK_200_WHEN_PRIVATE)) {
            return Optional.empty();
        }
        Standing me = standing.get();
        Optional<Rank> rank = arena.rankOf(callerId, me.archetype());
        if (rank.isEmpty()) { // private, or a revoked token: not on the board
            return Optional.of(new RankResponse(me.archetype(), me.publicProfile(), null, arena.count(me.archetype()),
                    null));
        }
        Rank mine = rank.get();
        return Optional.of(new RankResponse(me.archetype(), me.publicProfile(), mine.rank(), mine.total(),
                Arena.topPercent(mine.rank(), mine.total())));
    }
}
