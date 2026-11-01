package com.tushar27x.insightshub.arena;

/**
 * The Arena's open product choices (Phase 13), kept here so each is a one-line change. The fourth,
 * whether {@code public_profile} starts on, is the column default in {@code V1__users_and_insights.sql}
 * ({@code FALSE}: opt-in); turning it on for new users would take a new migration.
 */
public final class Arena {

    /** Open question 4: rows per leaderboard page when {@code size} is left out. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Open question 4: the largest {@code size} accepted (more is a validation 400). */
    public static final int MAX_PAGE_SIZE = 50;

    /**
     * Open question 2: whether {@code GET /api/user/rank} still answers 200 for a private profile (with
     * {@code rank: null} and the board's size), instead of a 404.
     */
    public static final boolean RANK_200_WHEN_PRIVATE = true;

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
