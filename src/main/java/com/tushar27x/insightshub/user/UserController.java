package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.error.TooManyRequestsException;
import com.tushar27x.insightshub.ratelimit.RateLimit;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import com.tushar27x.insightshub.sync.SyncService;
import com.tushar27x.insightshub.sync.SyncService.SyncResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
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
@Tag(name = "Insights", description = "The GitHub stats, class scores and AI reviews from the last sync.")
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
    @Operation(summary = "The insights from the last sync",
            description = "Cached for 24 h, evicted on sync. heatmap_labels cover only the 7-day week. "
                    + "repo_details holds README text meant as AI input: not for display.")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "The insights")
    @ApiResponse(responseCode = "404", description = "No insights yet (before the first sync): NEW GAME.")
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
    @Operation(summary = "Sync now and return the fresh insights",
            description = "Synchronous: usually under 30 s, at worst about 90 s (GitHub, then five LLM calls). "
                    + "Also refreshes today's activity, so GET /hero shows a new contribution right away.")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "The fresh insights")
    @ApiResponse(responseCode = "404", description = "Rare: the sync wrote nothing; treat as NEW GAME.")
    @ApiResponse(responseCode = "409", description = "GitHub no longer accepts the stored token (needs_reauth): "
            + "log in again.")
    @ApiResponse(responseCode = "429", description = "The per-user 10-minute cooldown (also after a failed sync), "
            + "or too many syncs across all users right now. No X-RateLimit-* headers here.",
            headers = @Header(name = HttpHeaders.RETRY_AFTER, description = "Whole seconds to wait, at least 1",
                    schema = @Schema(type = "integer")))
    @ApiResponse(responseCode = "502", description = "GitHub is unavailable.")
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
