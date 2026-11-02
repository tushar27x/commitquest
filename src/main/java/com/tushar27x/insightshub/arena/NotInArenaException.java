package com.tushar27x.insightshub.arena;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * {@code GET /api/user/rank} for a caller who has a hero but isn't on their class's board: a private profile, or a
 * revoked GitHub token ({@code needs_reauth}). A 404 like "no hero yet", told apart by its {@code title}
 * (the developer's decision, 2026-11-01).
 */
public class NotInArenaException extends ErrorResponseException {

    public static final String TITLE = "Not in the Arena";
    public static final String DETAIL = "Your profile isn't ranked. Turn on \"Show me in the Arena\" in Settings, "
            + "then log in again to refresh your stats.";

    public NotInArenaException() {
        super(HttpStatus.NOT_FOUND, problem(), null);
    }

    private static ProblemDetail problem() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, DETAIL);
        problem.setTitle(TITLE);
        return problem;
    }
}
