package com.tushar27x.insightshub.arena;

/**
 * The Arena's product choices (Phase 13, decided by the developer on 2026-11-01), kept here so each is a one-line
 * change. Also decided: {@code public_profile} starts off (the column default in {@code V1__users_and_insights.sql},
 * opt-in), a caller who isn't ranked gets a 404 ({@link NotInArenaException}), and the leaderboard row fields
 * ({@link LeaderboardResponse.Entry}).
 */
public final class Arena {

    /** Rows per leaderboard page when {@code size} is left out. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** The largest {@code size} accepted (more is a validation 400). */
    public static final int MAX_PAGE_SIZE = 50;

    private Arena() {
    }

    /**
     * "Top N%": the smallest whole percentage of the board at or above this rank, 1 to 100.
     * Rank 1 of 40 is the top 3%, rank 40 of 40 the top 100%.
     */
    public static int topPercent(int rank, int total) {
        if (rank < 1 || total < rank) {
            throw new IllegalArgumentException("rank " + rank + " is not on a board of " + total);
        }
        return (int) Math.max(1, (100L * rank + total - 1) / total);
    }

    /** GitHub serves every account's avatar by its numeric id, so the URL needs no stored column or API call. */
    public static String avatarUrl(long githubId) {
        return "https://avatars.githubusercontent.com/u/" + githubId + "?v=4";
    }
}
