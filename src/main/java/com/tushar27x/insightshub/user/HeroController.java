package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.ratelimit.RateLimit;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Hero", description = "The hero pet: hearts, XP, level, mood, the week and the class.")
@RestController
@RequestMapping("/api/user/hero")
public class HeroController {

    private final HeroQueryService heroQueryService;
    private final HeroClassService heroClassService;

    public HeroController(HeroQueryService heroQueryService, HeroClassService heroClassService) {
        this.heroQueryService = heroQueryService;
        this.heroClassService = heroClassService;
    }

    @Operation(summary = "The hero now",
            description = "mood is computed on each request. class.current = chosen ?? scored; "
                    + "class.can_switch_at is null when a switch is allowed now.")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "The hero")
    @ApiResponse(responseCode = "404", description = "No hero yet (before the first sync): NEW GAME.")
    @RateLimit(policy = "hero", key = KeyType.USER)
    @GetMapping
    public HeroResponse hero(@AuthenticationPrincipal Jwt jwt) {
        return heroQueryService.hero(githubId(jwt)).orElseThrow(HeroController::noHero);
    }

    /** The timeline of the last {@code days} (1–90) local days, newest last. */
    @Operation(summary = "The hero's timeline",
            description = "Events and XP days of the last days local days (today − days … today), newest last. "
                    + "Event details: LIFE_LOST {lives, reason}, LIFE_GAINED {lives, streak}, LEVEL_UP and LEVEL_DOWN "
                    + "{from, to}, FAINTED and RESTED {}, CLASS_CHANGED {from, to, chosen}.")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "The timeline")
    @ApiResponse(responseCode = "400", description = "days outside 1–90: a validation problem with "
            + "errors[{field: \"days\"}]. Not a number: Spring's own 400, no errors[].")
    @ApiResponse(responseCode = "404", description = "No hero yet (before the first sync).")
    @RateLimit(policy = "hero", key = KeyType.USER)
    @GetMapping("/events")
    public HeroEventsResponse events(@AuthenticationPrincipal Jwt jwt,
                                     @Parameter(description = "Local days to cover, 1–90")
                                     @RequestParam(defaultValue = "30") @Min(1) @Max(90) int days) {
        return heroQueryService.events(githubId(jwt), days).orElseThrow(HeroController::noHero);
    }

    /**
     * Switches the class, or {@code {"archetype": null}} to follow the top score. 200 with the updated hero,
     * 409 if not qualified, 429 + {@code Retry-After} within the 7-day cooldown.
     */
    @Operation(summary = "Choose the hero's class",
            description = "{\"archetype\": null} follows the top score again. Checks in order: the same as the stored "
                    + "choice → 200 no-op; not qualified → 409; within the 7-day cooldown → 429; otherwise the change, "
                    + "with a CLASS_CHANGED event. Level, XP, lives and streak don't change.")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "The updated hero (as GET /hero)")
    @ApiResponse(responseCode = "400", description = "An unknown class name: Spring's own 400 (no errors[]).")
    @ApiResponse(responseCode = "404", description = "No hero yet (before the first sync).")
    @ApiResponse(responseCode = "409", description = "Not qualified: the class scores under 40 and isn't the top "
            + "scored class.")
    @ApiResponse(responseCode = "429", description = "Within 7 days of the last class change.")
    @RateLimit(policy = "hero-class", key = KeyType.USER)
    @PutMapping("/class")
    public HeroResponse changeClass(@AuthenticationPrincipal Jwt jwt,
                                    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                                            examples = {
                                                    @ExampleObject(name = "choose",
                                                            value = "{\"archetype\": \"MONK\"}"),
                                                    @ExampleObject(name = "follow the top score",
                                                            value = "{\"archetype\": null}")}))
                                    @RequestBody ClassRequest request) {
        return heroClassService.change(githubId(jwt), request.archetype()).orElseThrow(HeroController::noHero);
    }

    private static ResponseStatusException noHero() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No hero yet for this user; sync first");
    }

    static long githubId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
