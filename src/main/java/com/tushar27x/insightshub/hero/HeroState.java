package com.tushar27x.insightshub.hero;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The stored hero ({@code hero_state}). Only valid states can be built; the same rules are CHECKs in V5.
 *
 * @param xp              progress inside the current level: {@code 0 <= xp < 50 × level}
 * @param lives           1 to 3: reaching 0 refills them at once (level-down or faint)
 * @param streak          consecutive active days; rest days pause it
 * @param lastEvaluatedOn the last day settled. The next evaluation must be the day after it
 */
public record HeroState(int level, int xp, int lives, int streak, LocalDate lastEvaluatedOn) {

    public HeroState {
        if (level < 1) {
            throw new IllegalArgumentException("level must be at least 1, was " + level);
        }
        if (xp < 0 || xp >= HeroRules.xpForNext(level)) {
            throw new IllegalArgumentException("xp must be in [0, " + HeroRules.xpForNext(level) + "), was " + xp);
        }
        if (lives < 1 || lives > HeroRules.MAX_LIVES) {
            throw new IllegalArgumentException("lives must be in [1, " + HeroRules.MAX_LIVES + "], was " + lives);
        }
        if (streak < 0) {
            throw new IllegalArgumentException("streak can't be negative, was " + streak);
        }
        Objects.requireNonNull(lastEvaluatedOn, "lastEvaluatedOn");
    }
}
