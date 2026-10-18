package com.tushar27x.insightshub.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code insightshub.auth.*}: the GitHub OAuth App and the app's own JWTs.
 *
 * @param githubClientId     OAuth App client id
 * @param githubClientSecret OAuth App client secret
 * @param scopes             public data + verified email only; no {@code repo}, because the token is stored
 * @param tokenTtl           lifetime of the JWT handed out at login
 * @param redirectToFrontend send the browser to {@code FRONTEND_URL/dashboard#token=...} (the web app's hand-off,
 *                           a fragment so the JWT never reaches a server log) instead of returning the token as JSON
 */
@Validated
@ConfigurationProperties(prefix = "insightshub.auth")
public record AuthProperties(
        @NotBlank String githubClientId,
        @NotBlank String githubClientSecret,
        @DefaultValue("read:user user:email") String scopes,
        @DefaultValue("24h") Duration tokenTtl,
        @DefaultValue("false") boolean redirectToFrontend) {
}
