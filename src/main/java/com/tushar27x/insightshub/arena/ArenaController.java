package com.tushar27x.insightshub.arena;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.ratelimit.RateLimit;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Arena", description = "Per-class leaderboards of opted-in heroes, ranked by level, then XP.")
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
    @Operation(summary = "One page of a class's board, best first",
            description = "On a board: heroes whose current class is that class, with public_profile on and working "
                    + "GitHub access. Ties share a rank (1, 2, 2, 4) and are listed by GitHub id. Any signed-in user "
                    + "can read any board. entries is [] past the last page; pages = ceil(total / size).")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "The page")
    @ApiResponse(responseCode = "400", description = "page < 0 or size outside 1–50: a validation problem with "
            + "errors[{field: \"page\" | \"size\"}]. Not a number, or an unknown or lower-case class: Spring's own "
            + "400, no errors[].")
    @ApiResponse(responseCode = "404", description = "No archetype given and the caller has no hero yet.")
    @RateLimit(policy = "arena", key = KeyType.USER)
    @GetMapping("/api/leaderboard")
    public LeaderboardResponse leaderboard(@AuthenticationPrincipal Jwt jwt,
                                           @Parameter(description = "The board; default: the caller's current class")
                                           @RequestParam(required = false) Archetype archetype,
                                           @Parameter(description = "Zero-based page")
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @Parameter(description = "Page size, 1–50")
                                           @RequestParam(defaultValue = "" + Arena.DEFAULT_PAGE_SIZE)
                                           @Min(1) @Max(Arena.MAX_PAGE_SIZE) int size) {
        return arenaService.leaderboard(githubId(jwt), archetype, page, size)
                .orElseThrow(() -> noHero("No hero yet for this user; sync first, or pass an archetype"));
    }

    /**
     * The caller's rank on their current class's board. 404 "Not in the Arena" when they aren't on it (private, or a
     * revoked GitHub token), and the usual 404 before the first sync; the UI tells them apart by {@code title}.
     */
    @Operation(summary = "The caller's rank on their current class's board",
            description = "top_percent = ceil(100 × rank / total). The caller's leaderboard page is "
                    + "floor((rank − 1) / size).")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "The rank")
    @ApiResponse(responseCode = "404", description = "Two cases, told apart by title (the one exception to the "
            + "convention): title \"Not in the Arena\" when the caller has a hero but isn't on the board (a private "
            + "profile, or GitHub access to renew); title \"Not Found\" before the first sync.")
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
