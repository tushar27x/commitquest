package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.error.TooManyRequestsException;
import com.tushar27x.insightshub.ratelimit.RateLimit;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import com.tushar27x.insightshub.sync.SyncService;
import com.tushar27x.insightshub.sync.SyncService.SyncResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoints for the signed-in user. The JWT's {@code sub} is the GitHub id; the user is
 * never taken from a request parameter, so nobody can read someone else's data.
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final InsightsQueryService insightsQueryService;
    private final SyncService syncService;

    public UserController(InsightsQueryService insightsQueryService, SyncService syncService) {
        this.insightsQueryService = insightsQueryService;
        this.syncService = syncService;
    }

    /** The insights from the last sync, served from Redis after the first read. 404 until the first sync. */
    @RateLimit(policy = "insights", key = KeyType.USER)
    @GetMapping("/insights")
    public InsightsResponse insights(@AuthenticationPrincipal Jwt jwt) {
        return insightsOf(githubId(jwt));
    }

    /**
     * Refreshes the user's insights now and returns them. At most one sync per user every
     * {@code insightshub.sync.min-interval} (10 min), and only while the global sync cap has room;
     * otherwise 429 with {@code Retry-After}.
     * Synchronous: a sync takes about 10–30 s (GitHub + five LLM calls).
     */
    @PostMapping("/sync")
    public InsightsResponse refresh(@AuthenticationPrincipal Jwt jwt) {
        long githubId = githubId(jwt);
        SyncResult result = syncService.syncIfDue(githubId); // GitHub down → 502, by ApiExceptionHandler
        return switch (result.outcome()) {
            case SYNCED -> insightsOf(githubId);
            case COOLDOWN -> throw new TooManyRequestsException("You synced recently; try again later",
                    result.retryAfter());
            // too many syncs across all users right now; same answer for the client: wait and retry
            case BUSY -> throw new TooManyRequestsException("Too many syncs right now; try again shortly",
                    result.retryAfter());
            case NEEDS_REAUTH -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "GitHub no longer accepts your token; log in again at /api/auth/login");
        };
    }

    private InsightsResponse insightsOf(long githubId) {
        return insightsQueryService.getInsights(githubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No insights yet for this user"));
    }

    private static long githubId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
