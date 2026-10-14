package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.sync.SyncService;
import com.tushar27x.insightshub.sync.SyncService.SyncOutcome;
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

import java.time.Clock;
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
    private final Clock clock;

    public UserController(InsightsQueryService insightsQueryService, SyncService syncService, Clock clock) {
        this.insightsQueryService = insightsQueryService;
        this.syncService = syncService;
        this.clock = clock;
    }

    /** The insights from the last sync, served from Redis after the first read. 404 until the first sync. */
    @GetMapping("/insights")
    public InsightsResponse insights(@AuthenticationPrincipal Jwt jwt) {
        return insightsOf(githubId(jwt));
    }

    /**
     * Refreshes the user's insights now and returns them. At most one sync per user every
     * {@code insightshub.sync.min-interval} (10 min); earlier calls get 429 with {@code Retry-After}.
     * Synchronous: a sync takes about 10–30 s (GitHub + five LLM calls).
     */
    @PostMapping("/sync")
    public ResponseEntity<InsightsResponse> refresh(@AuthenticationPrincipal Jwt jwt) {
        long githubId = githubId(jwt);
        SyncOutcome outcome;
        try {
            outcome = syncService.syncIfDue(githubId);
        } catch (GitHubApiException e) {
            log.warn("Refresh for user {} failed: {}", githubId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub is unavailable; try again later", e);
        }
        return switch (outcome) {
            case SYNCED -> ResponseEntity.ok(insightsOf(githubId));
            case COOLDOWN -> {
                long seconds = Duration.between(clock.instant(), syncService.nextSyncAllowedAt(githubId)).toSeconds();
                yield ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, seconds)))
                        .build();
            }
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
