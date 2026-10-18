package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.ratelimit.RateLimit;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
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
@RestController
@RequestMapping("/api/user/preferences")
public class PreferencesController {

    private final PreferencesService preferencesService;

    public PreferencesController(PreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @RateLimit(policy = "preferences", key = KeyType.USER)
    @GetMapping
    public Preferences get(@AuthenticationPrincipal Jwt jwt) {
        return preferencesService.get(HeroController.githubId(jwt)).orElseThrow(PreferencesController::noUser);
    }

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
