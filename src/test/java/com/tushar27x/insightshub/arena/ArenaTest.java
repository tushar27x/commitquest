package com.tushar27x.insightshub.arena;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArenaTest {

    @Test
    void topPercentRoundsUpSoTheLeaderIsNeverTopZero() {
        assertThat(Arena.topPercent(1, 40)).isEqualTo(3);   // 2.5 -> 3
        assertThat(Arena.topPercent(1, 1000)).isEqualTo(1); // 0.1 -> 1
        assertThat(Arena.topPercent(4, 37)).isEqualTo(11);  // 10.8 -> 11
        assertThat(Arena.topPercent(10, 100)).isEqualTo(10);
        assertThat(Arena.topPercent(1, 1)).isEqualTo(100);
        assertThat(Arena.topPercent(40, 40)).isEqualTo(100);
    }

    @Test
    void topPercentDoesNotOverflowOnALargeBoard() {
        assertThat(Arena.topPercent(Integer.MAX_VALUE, Integer.MAX_VALUE)).isEqualTo(100);
    }

    @Test
    void aRankOffTheBoardIsABug() {
        assertThatThrownBy(() -> Arena.topPercent(0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Arena.topPercent(11, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theAvatarComesFromTheGitHubId() {
        assertThat(Arena.avatarUrl(583231L)).isEqualTo("https://avatars.githubusercontent.com/u/583231?v=4");
    }

    @Test
    void thePageSizeDefaultFitsItsMaximum() {
        assertThat(Arena.DEFAULT_PAGE_SIZE).isBetween(1, Arena.MAX_PAGE_SIZE);
    }
}
