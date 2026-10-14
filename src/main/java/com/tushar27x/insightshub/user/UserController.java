package com.tushar27x.insightshub.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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

    public UserController(InsightsQueryService insightsQueryService) {
        this.insightsQueryService = insightsQueryService;
    }

    /** The insights from the last sync, served from Redis after the first read. 404 until the first sync. */
    @GetMapping("/insights")
    public InsightsResponse insights(@AuthenticationPrincipal Jwt jwt) {
        long githubId = Long.parseLong(jwt.getSubject());
        return insightsQueryService.getInsights(githubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No insights yet for this user"));
    }
}
