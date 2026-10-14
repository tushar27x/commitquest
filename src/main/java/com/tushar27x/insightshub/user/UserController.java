package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.ratelimit.RateLimit;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import com.tushar27x.insightshub.sync.SyncService;
import com.tushar27x.insightshub.sync.SyncService.SyncResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Endpoints for the signed-in user. The JWT's {@code sub} is the GitHub id; the user is
 * never taken from a request parameter, so nobody can read someone else's data.
 */
@Slf4j
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
    public ResponseEntity<InsightsResponse> refresh(@AuthenticationPrincipal Jwt jwt) {
        long githubId = githubId(jwt);
        SyncResult result;
        try {
            result = syncService.syncIfDue(githubId);
        } catch (GitHubApiException e) {
            log.warn("Refresh for user {} failed: {}", githubId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub is unavailable; try again later", e);
        }
        return switch (result.outcome()) {
            case SYNCED -> ResponseEntity.ok(insightsOf(githubId));
            // BUSY: too many syncs across all users right now; same answer for the client: wait and retry
            case COOLDOWN, BUSY -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds(result.retryAfter())))
                    .build();
            case NEEDS_REAUTH -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "GitHub no longer accepts your token; log in again at /api/auth/login");
        };
    }

    /** Whole seconds, rounded up, at least 1 (a {@code Retry-After} of 0 would invite an instant retry). */
    static long retryAfterSeconds(Duration wait) {
        return Math.max(1, (wait.toMillis() + 999) / 1000);
    }

    private InsightsResponse insightsOf(long githubId) {
        return insightsQueryService.getInsights(githubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No insights yet for this user"));
    }

    private static long githubId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
