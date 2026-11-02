package com.tushar27x.insightshub.arena;

import com.tushar27x.insightshub.entity.Archetype;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * {@code GET /api/user/rank}: the caller's place on the board of their current class. Only sent when the caller is
 * on it; otherwise a 404 ({@link NotInArenaException}, or "no hero yet").
 *
 * @param archetype  the caller's current class ({@code class.current} of {@code GET /hero}): the board they're on
 * @param total      heroes on that board, the caller included
 * @param topPercent {@code ceil(100 × rank / total)}, 1 to 100
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RankResponse(Archetype archetype, int rank, int total, int topPercent) {
}
