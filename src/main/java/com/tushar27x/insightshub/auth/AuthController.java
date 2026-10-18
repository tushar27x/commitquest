package com.tushar27x.insightshub.auth;

import com.tushar27x.insightshub.auth.AuthService.LoginResult;
import com.tushar27x.insightshub.auth.GitHubOAuthClient.OAuthCodeRejectedException;
import com.tushar27x.insightshub.config.InsightsHubProperties;
import com.tushar27x.insightshub.ratelimit.RateLimit;
import com.tushar27x.insightshub.ratelimit.RateLimit.KeyType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * GitHub login, same paths as v1. {@code state} is a random value kept in an HttpOnly cookie
 * for the round trip; the callback only accepts a {@code state} matching it, which stops an
 * attacker from completing a login with their own code in the victim's browser (login CSRF).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String STATE_COOKIE = "oauth_state";
    static final String CALLBACK_PATH = "/api/auth/callback";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final int STATE_BYTES = 32;
    private static final int MAX_PARAM = 256;

    private final AuthService authService;
    private final AuthProperties authProperties;
    private final String backendUrl;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public AuthController(AuthService authService, AuthProperties authProperties, InsightsHubProperties properties) {
        this.authService = authService;
        this.authProperties = authProperties;
        this.backendUrl = stripTrailingSlash(properties.backendUrl());
        this.frontendUrl = stripTrailingSlash(properties.frontendUrl());
    }

    @RateLimit(policy = "login", key = KeyType.IP)
    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        String state = newState();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(authService.authorizeUri(state, callbackUri()))
                .header(HttpHeaders.SET_COOKIE, stateCookie(state, STATE_TTL).toString())
                .build();
    }

    /**
     * GitHub sends {@code code} on success, or {@code error} (e.g. {@code access_denied}) when the user cancels.
     * The size limits only bound what we accept; {@code error} is echoed in the 400, so it must be a plain code.
     */
    @RateLimit(policy = "callback", key = KeyType.IP) // each callback costs 3 GitHub calls and may sync
    @GetMapping("/callback")
    public ResponseEntity<LoginResponse> callback(@RequestParam(required = false) @Size(max = MAX_PARAM) String code,
                                                  @RequestParam(required = false) @Size(max = MAX_PARAM) String state,
                                                  @RequestParam(required = false) @Size(max = MAX_PARAM)
                                                  @Pattern(regexp = "[a-z_]+", message = "must be a GitHub error code")
                                                  String error,
                                                  @CookieValue(name = STATE_COOKIE, required = false) String expectedState) {
        if (!sameState(expectedState, state)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OAuth state; start again at /api/auth/login");
        }
        if (error != null || code == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub login did not complete: " + (error == null ? "no code" : error));
        }

        LoginResult result;
        try {
            result = authService.completeLogin(code, callbackUri());
        } catch (OAuthCodeRejectedException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub rejected the login: " + e.error(), e);
        }

        String clearState = stateCookie("", Duration.ZERO).toString(); // one login per state
        if (authProperties.redirectToFrontend()) {
            return ResponseEntity.status(HttpStatus.FOUND).location(dashboardUri(frontendUrl, result.token().value()))
                    .header(HttpHeaders.SET_COOKIE, clearState).build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearState)
                .body(new LoginResponse(result.token().value(), "Bearer", result.token().expiresAt(), result.login()));
    }

    /** JWTs are stateless: nothing to revoke server-side, the client drops its token. Kept for v1's contract. */
    @GetMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(frontendUrl)).build();
    }

    /**
     * {@code FRONTEND_URL/dashboard#token=<jwt>}. A fragment, not a query string: browsers never send it to a
     * server, so the JWT stays out of access logs, proxies and {@code Referer} headers. The web app reads it and
     * clears it from the address bar.
     */
    static URI dashboardUri(String frontendUrl, String jwt) {
        return UriComponentsBuilder.fromUriString(frontendUrl + "/dashboard")
                .fragment("token=" + jwt)
                .build().toUri();
    }

    private String callbackUri() {
        return backendUrl + CALLBACK_PATH;
    }

    private String newState() {
        byte[] bytes = new byte[STATE_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ResponseCookie stateCookie(String value, Duration maxAge) {
        return ResponseCookie.from(STATE_COOKIE, value)
                .httpOnly(true)
                .secure(backendUrl.startsWith("https://"))
                .sameSite("Lax") // sent on GitHub's top-level redirect back to us
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }

    /** Constant-time comparison, so response timing doesn't leak how much of the state matched. */
    private static boolean sameState(String expected, String actual) {
        if (expected == null || expected.isEmpty() || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, String login) {
    }
}
