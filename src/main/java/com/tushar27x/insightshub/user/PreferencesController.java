package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.ratelimit.RateLimit;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The signed-in user's settings: zone, rest days, email alerts, Arena opt-in. 400 with {@code errors[]} on invalid input. */
@Tag(name = "Preferences", description = "Time zone, rest days, email alerts and the Arena opt-in.")
@RestController
@RequestMapping("/api/user/preferences")
public class PreferencesController {

    private final PreferencesService preferencesService;

    public PreferencesController(PreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @Operation(summary = "The user's settings")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "The settings")
    @ApiResponse(responseCode = "404", description = "The JWT's user no longer exists.")
    @RateLimit(policy = "preferences", key = KeyType.USER)
    @GetMapping
    public Preferences get(@AuthenticationPrincipal Jwt jwt) {
        return preferencesService.get(HeroController.githubId(jwt)).orElseThrow(PreferencesController::noUser);
    }

    @Operation(summary = "Save the user's settings",
            description = "The full object: timezone, rest_days, alerts_enabled, public_profile and include_private are required; private_access is read-only and ignored. timezone is an IANA region id "
                    + "(+05:30 and GMT+5 are refused); rest_days has at most 2 days, none repeated, and a change "
                    + "applies from the next local day.")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true, description = "Saved")
    @ApiResponse(responseCode = "400", description = "A validation problem with errors[{field, message}], field the "
            + "snake_case name. An unknown weekday string is a JSON error: Spring's own 400, no errors[].")
    @ApiResponse(responseCode = "404", description = "The JWT's user no longer exists.")
    @RateLimit(policy = "preferences", key = KeyType.USER)
    @PutMapping
    public Preferences put(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Preferences preferences) {
        return preferencesService.update(HeroController.githubId(jwt), preferences)
                .orElseThrow(PreferencesController::noUser);
    }

    /** A valid JWT for a user that was deleted since. */
    private static ResponseStatusException noUser() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No such user");
    }
}
