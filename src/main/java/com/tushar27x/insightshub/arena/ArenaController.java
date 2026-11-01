package com.tushar27x.insightshub.arena;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.ratelimit.RateLimit;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Arena (Phase 13): {@code GET /api/leaderboard} and {@code GET /api/user/rank}, both behind the JWT and
 * sharing the {@code arena} rate-limit policy. The caller comes from the JWT's {@code sub}, never from a parameter.
 */
@RestController
public class ArenaController {

    private final ArenaService arenaService;

    public ArenaController(ArenaService arenaService) {
        this.arenaService = arenaService;
    }

    /**
     * One page of a class's board. Without {@code archetype}, the caller's current class (404 before the first
     * sync). An unknown class is Spring's own 400; {@code page} below 0 or {@code size} outside 1 to
     * {@link Arena#MAX_PAGE_SIZE} is a validation 400.
     */
    @RateLimit(policy = "arena", key = KeyType.USER)
    @GetMapping("/api/leaderboard")
    public LeaderboardResponse leaderboard(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(required = false) Archetype archetype,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "" + Arena.DEFAULT_PAGE_SIZE)
                                           @Min(1) @Max(Arena.MAX_PAGE_SIZE) int size) {
        return arenaService.leaderboard(githubId(jwt), archetype, page, size)
                .orElseThrow(() -> noHero("No hero yet for this user; sync first, or pass an archetype"));
    }

    /**
     * The caller's rank on their current class's board. 404 "Not in the Arena" when they aren't on it (private, or a
     * revoked GitHub token), and the usual 404 before the first sync; the UI tells them apart by {@code title}.
     */
    @RateLimit(policy = "arena", key = KeyType.USER)
    @GetMapping("/api/user/rank")
    public RankResponse rank(@AuthenticationPrincipal Jwt jwt) {
        return arenaService.rank(githubId(jwt))
                .orElseThrow(() -> noHero("No hero yet for this user; sync first"));
    }

    private static ResponseStatusException noHero(String detail) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, detail);
    }

    private static long githubId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
