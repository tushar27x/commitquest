package com.tushar27x.insightshub.arena;

import io.swagger.v3.oas.annotations.media.Schema;
import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.hero.Tier;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * {@code GET /api/leaderboard}: one page of a class's board, best first.
 *
 * @param total heroes on the whole board (all pages)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LeaderboardResponse(Archetype archetype, int page, int size, int total, List<Entry> entries) {

    /**
     * One hero on the board.
     *
     * @param rank  shared by heroes with the same level and XP (1, 2, 2, 4)
     * @param isYou whether this row is the caller's hero
     */
    @Schema(name = "LeaderboardEntry")
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Entry(int rank, String login, String avatarUrl, int level, int xp, int lives, Tier tier,
                        boolean isYou) {
    }
}
