package com.tushar27x.insightshub.arena;

import com.tushar27x.insightshub.entity.Archetype;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * {@code GET /api/user/rank}: the caller's place on the board of their current class.
 *
 * @param archetype     the caller's current class ({@code class.current} of {@code GET /hero}): the board they're on
 * @param publicProfile the caller's "Show me in the Arena" setting
 * @param rank          null when the caller isn't on the board (private profile, or a revoked GitHub token)
 * @param total         heroes on that board (not counting a caller who isn't on it)
 * @param topPercent    {@code ceil(100 × rank / total)}, null with {@code rank}
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RankResponse(Archetype archetype, boolean publicProfile, Integer rank, int total, Integer topPercent) {
}
