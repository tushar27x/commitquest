package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.entity.Insights;
import com.tushar27x.insightshub.repository.InsightsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.Map;

/**
 * Endpoints for the signed-in user. The JWT's {@code sub} is the GitHub id; the user is
 * never taken from a request parameter, so nobody can read someone else's data.
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final InsightsRepository insightsRepository;

    public UserController(InsightsRepository insightsRepository) {
        this.insightsRepository = insightsRepository;
    }

    /**
     * The stored insights, in v1's response shape. Phase 7 puts this behind a cached query
     * service and runs the sync that fills it; until then, a user who never synced gets 404.
     */
    @GetMapping("/insights")
    public InsightsResponse insights(@AuthenticationPrincipal Jwt jwt) {
        long githubId = Long.parseLong(jwt.getSubject());
        return insightsRepository.findById(githubId)
                .map(InsightsResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No insights yet for this user"));
    }

    /** DTO, never the entity (no lazy-loading surprises, no accidental token leaks). */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InsightsResponse(
            String userName,
            Archetype archetype,
            Map<String, Object> stats,
            String displayJson,
            String weeklyReview,
            String craftReview,
            Instant updatedAt) {

        static InsightsResponse from(Insights insights) {
            return new InsightsResponse(
                    insights.getUser().getLogin(),
                    insights.getArchetype(),
                    insights.getStats(),
                    insights.getRoast(),
                    insights.getWeeklyReview(),
                    insights.getCraftReview(),
                    insights.getUpdatedAt());
        }
    }
}
