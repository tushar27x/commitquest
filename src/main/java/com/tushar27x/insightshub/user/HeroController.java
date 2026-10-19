package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.ratelimit.RateLimit;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The signed-in user's hero. The user comes from the JWT's {@code sub}, never from a parameter. 404 before the first sync. */
@RestController
@RequestMapping("/api/user/hero")
public class HeroController {

    private final HeroQueryService heroQueryService;
    private final HeroClassService heroClassService;

    public HeroController(HeroQueryService heroQueryService, HeroClassService heroClassService) {
        this.heroQueryService = heroQueryService;
        this.heroClassService = heroClassService;
    }

    @RateLimit(policy = "hero", key = KeyType.USER)
    @GetMapping
    public HeroResponse hero(@AuthenticationPrincipal Jwt jwt) {
        return heroQueryService.hero(githubId(jwt)).orElseThrow(HeroController::noHero);
    }

    /** The timeline of the last {@code days} (1–90) local days, newest last. */
    @RateLimit(policy = "hero", key = KeyType.USER)
    @GetMapping("/events")
    public HeroEventsResponse events(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(defaultValue = "30") @Min(1) @Max(90) int days) {
        return heroQueryService.events(githubId(jwt), days).orElseThrow(HeroController::noHero);
    }

    /**
     * Switches the class, or {@code {"archetype": null}} to follow the top score. 200 with the updated hero,
     * 409 if not qualified, 429 + {@code Retry-After} within the 7-day cooldown.
     */
    @RateLimit(policy = "hero-class", key = KeyType.USER)
    @PutMapping("/class")
    public HeroResponse changeClass(@AuthenticationPrincipal Jwt jwt, @RequestBody ClassRequest request) {
        return heroClassService.change(githubId(jwt), request.archetype()).orElseThrow(HeroController::noHero);
    }

    private static ResponseStatusException noHero() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No hero yet for this user; sync first");
    }

    static long githubId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
