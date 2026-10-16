package com.tushar27x.insightshub.auth;

import com.jayway.jsonpath.JsonPath;
import com.tushar27x.insightshub.auth.GitHubOAuthClient.GitHubAccount;
import com.tushar27x.insightshub.auth.GitHubOAuthClient.OAuthCodeRejectedException;
import com.tushar27x.insightshub.config.ClockConfig;
import com.tushar27x.insightshub.config.InsightsHubProperties;
import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.JwtConfig;
import com.tushar27x.insightshub.security.JwtService;
import com.tushar27x.insightshub.security.SecurityConfig;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.security.TokenCipher;
import com.tushar27x.insightshub.github.GitHubApiException;
import com.tushar27x.insightshub.sync.SyncService;
import com.tushar27x.insightshub.sync.SyncService.SyncOutcome;
import com.tushar27x.insightshub.sync.SyncService.SyncResult;
import com.tushar27x.insightshub.ratelimit.InMemoryRateLimiter;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import com.tushar27x.insightshub.user.InsightsQueryService;
import com.tushar27x.insightshub.user.InsightsResponse;
import com.tushar27x.insightshub.user.UserController;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real security filter chain, JWT issuing/validation and controllers; GitHub and the
 * database are mocked. No network, no DB (B23).
 */
@WebMvcTest(controllers = {AuthController.class, UserController.class}, properties = {
        "insightshub.frontend-url=http://localhost:3000/",
        "insightshub.backend-url=http://localhost:8080",
        "insightshub.jwt-secret=" + SecurityTestData.JWT_SECRET,
        "insightshub.auth.github-client-id=client-id",
        "insightshub.auth.github-client-secret=client-secret",
        // the real rate-limit interceptor is part of the slice; limits high enough not to interfere here
        "insightshub.rate-limit.policies.login.requests=1000",
        "insightshub.rate-limit.policies.login.per=1m",
        "insightshub.rate-limit.policies.callback.requests=1000",
        "insightshub.rate-limit.policies.callback.per=1m",
        "insightshub.rate-limit.policies.insights.requests=1000",
        "insightshub.rate-limit.policies.insights.per=1m",
})
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class, AuthService.class, TokenCipher.class,
        AuthApiTest.Properties.class})
class AuthApiTest {

    private static final long GITHUB_ID = 583231L;
    private static final String REDIRECT_URI = "http://localhost:8080/api/auth/callback";

    /** Not a compile-time constant (see {@link SecurityTestData}), so it can't go in {@code properties}. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        registry.add("insightshub.encryption-key", () -> SecurityTestData.ENCRYPTION_KEY);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private GitHubOAuthClient gitHub;
    @MockitoBean
    private UsersRepository usersRepository;
    @MockitoBean
    private InsightsQueryService insightsQueryService;
    @MockitoBean
    private SyncService syncService;

    // --- /api/user/** requires a valid JWT ---

    @Test
    void insightsWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/user/insights"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Missing, invalid or expired token; log in again"))
                .andExpect(jsonPath("$.instance").value("/api/user/insights"));
    }

    @Test
    void insightsWithGarbageTokenIs401() throws Exception {
        mvc.perform(get("/api/user/insights").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(content().string(not(containsString("not.a.jwt")))); // never echo the token
    }

    @Test
    void insightsWithExpiredTokenIs401() throws Exception {
        JwtService yesterday = new JwtService(JwtConfig.encoder(SecurityTestData.JWT_SECRET),
                Clock.fixed(Instant.now().minus(Duration.ofHours(25)), ZoneOffset.UTC), SecurityTestData.authProperties());
        String expired = yesterday.issue(GITHUB_ID, "octocat").value();

        mvc.perform(get("/api/user/insights").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void insightsWithValidTokenIs200AndOnlyForThatUser() throws Exception {
        when(insightsQueryService.getInsights(GITHUB_ID)).thenReturn(Optional.of(new InsightsResponse(
                "octocat", Archetype.MONK, Map.of("MONK", 52), Map.of("total_commits", 42),
                "Good year.", "Steady week.", "Clean commits.", Map.of(), null, Instant.now())));

        mvc.perform(get("/api/user/insights").header(HttpHeaders.AUTHORIZATION, bearer(GITHUB_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_name").value("octocat"))
                .andExpect(jsonPath("$.stats.total_commits").value(42))
                .andExpect(jsonPath("$.weekly_review").value("Steady week."))
                .andExpect(jsonPath("$.year_in_review").value("Good year."))
                .andExpect(jsonPath("$.encrypted_github_token").doesNotExist());
    }

    @Test
    void insightsBeforeFirstSyncIs404() throws Exception {
        when(insightsQueryService.getInsights(GITHUB_ID)).thenReturn(Optional.empty());

        mvc.perform(get("/api/user/insights").header(HttpHeaders.AUTHORIZATION, bearer(GITHUB_ID)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No insights yet for this user"))
                .andExpect(jsonPath("$.instance").value("/api/user/insights"));
    }

    // --- POST /api/user/sync: refresh, at most every 10 minutes ---

    @Test
    void refreshWithoutTokenIs401() throws Exception {
        mvc.perform(post("/api/user/sync")).andExpect(status().isUnauthorized());
        verifyNoInteractions(syncService);
    }

    @Test
    void refreshReturnsTheFreshInsights() throws Exception {
        when(syncService.syncIfDue(GITHUB_ID)).thenReturn(new SyncResult(SyncOutcome.SYNCED, Duration.ZERO));
        when(insightsQueryService.getInsights(GITHUB_ID)).thenReturn(Optional.of(new InsightsResponse(
                "octocat", Archetype.MONK, Map.of(), Map.of(), "Fresh year.", null, null, Map.of(), null, Instant.now())));

        mvc.perform(post("/api/user/sync").header(HttpHeaders.AUTHORIZATION, bearer(GITHUB_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year_in_review").value("Fresh year."));
    }

    @Test
    void refreshDuringCooldownIs429WithRetryAfter() throws Exception {
        when(syncService.syncIfDue(GITHUB_ID)).thenReturn(new SyncResult(SyncOutcome.COOLDOWN, Duration.ofMillis(359_400)));

        String retryAfter = mvc.perform(post("/api/user/sync").header(HttpHeaders.AUTHORIZATION, bearer(GITHUB_ID)))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("You synced recently; try again later"))
                .andExpect(jsonPath("$.instance").value("/api/user/sync"))
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);

        assertThat(retryAfter).isEqualTo("360"); // 359.4 s, rounded up
    }

    @Test
    void refreshWhileTooManyUsersSyncIs429() throws Exception {
        when(syncService.syncIfDue(GITHUB_ID)).thenReturn(new SyncResult(SyncOutcome.BUSY, Duration.ofSeconds(17)));

        mvc.perform(post("/api/user/sync").header(HttpHeaders.AUTHORIZATION, bearer(GITHUB_ID)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "17"))
                .andExpect(jsonPath("$.detail").value("Too many syncs right now; try again shortly"));
    }

    @Test
    void refreshWithRevokedGitHubTokenIs409() throws Exception {
        when(syncService.syncIfDue(GITHUB_ID)).thenReturn(new SyncResult(SyncOutcome.NEEDS_REAUTH, Duration.ZERO));

        mvc.perform(post("/api/user/sync").header(HttpHeaders.AUTHORIZATION, bearer(GITHUB_ID)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("GitHub no longer accepts your token; log in again at /api/auth/login"));
    }

    @Test
    void refreshWhileGitHubIsDownIs502() throws Exception {
        when(syncService.syncIfDue(GITHUB_ID)).thenThrow(new GitHubApiException("GitHub GraphQL errors for 'octocat'"));

        mvc.perform(post("/api/user/sync").header(HttpHeaders.AUTHORIZATION, bearer(GITHUB_ID)))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("GitHub is unavailable; try again later"))
                .andExpect(jsonPath("$.instance").value("/api/user/sync"));
    }

    // --- login round trip ---

    @Test
    void loginRedirectsToGitHubWithStateCookie() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/login"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("https://github.com/login/oauth/authorize?")))
                .andReturn();

        Cookie cookie = result.getResponse().getCookie(AuthController.STATE_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo(600);
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Lax");

        var params = UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build().getQueryParams();
        assertThat(params.getFirst("state")).isEqualTo(cookie.getValue());
        assertThat(params.getFirst("client_id")).isEqualTo("client-id");
    }

    @Test
    void fullRoundTripYieldsJwtThatOpensUserEndpoints() throws Exception {
        Cookie stateCookie = mvc.perform(get("/api/auth/login")).andReturn()
                .getResponse().getCookie(AuthController.STATE_COOKIE);
        when(gitHub.exchangeCode("the-code", REDIRECT_URI)).thenReturn("gho_secret");
        when(gitHub.fetchAccount("gho_secret")).thenReturn(new GitHubAccount(GITHUB_ID, "octocat"));
        when(gitHub.fetchPrimaryEmail("gho_secret")).thenReturn("octocat@example.com");
        when(usersRepository.findById(GITHUB_ID)).thenReturn(Optional.empty());
        when(usersRepository.save(any(Users.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MvcResult callback = mvc.perform(get("/api/auth/callback")
                        .param("code", "the-code")
                        .param("state", stateCookie.getValue())
                        .cookie(stateCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.login").value("octocat"))
                .andExpect(jsonPath("$.expires_at").exists())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andReturn();

        String jwt = JsonPath.read(callback.getResponse().getContentAsString(), "$.access_token");
        assertThat(jwt).doesNotContain("gho_secret");
        when(insightsQueryService.getInsights(GITHUB_ID)).thenReturn(Optional.empty());
        mvc.perform(get("/api/user/insights").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isNotFound()); // authenticated; the (mocked) sync wrote nothing
        verify(syncService).syncIfDue(GITHUB_ID);
    }

    @Test
    void callbackWithoutStateCookieIs400() throws Exception {
        mvc.perform(get("/api/auth/callback").param("code", "c").param("state", "s"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void callbackWithMismatchedStateIs400() throws Exception {
        mvc.perform(get("/api/auth/callback").param("code", "c").param("state", "attacker")
                        .cookie(new Cookie(AuthController.STATE_COOKIE, "victim")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelledLoginIs400() throws Exception {
        mvc.perform(get("/api/auth/callback").param("error", "access_denied").param("state", "s")
                        .cookie(new Cookie(AuthController.STATE_COOKIE, "s")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("GitHub login did not complete: access_denied"))
                .andExpect(jsonPath("$.instance").value("/api/auth/callback")); // path only, no query
    }

    @Test
    void callbackErrorThatIsNotAGitHubCodeIsRejectedBeforeItIsEchoed() throws Exception {
        mvc.perform(get("/api/auth/callback").param("error", "<script>").param("state", "s")
                        .cookie(new Cookie(AuthController.STATE_COOKIE, "s")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("error"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a GitHub error code"))
                .andExpect(content().string(not(containsString("<script>"))));
    }

    @Test
    void oversizedCallbackParamIs400() throws Exception {
        mvc.perform(get("/api/auth/callback").param("code", "c".repeat(257)).param("state", "s")
                        .cookie(new Cookie(AuthController.STATE_COOKIE, "s")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("code"));
        verifyNoInteractions(gitHub);
    }

    @Test
    void rejectedCodeIs400() throws Exception {
        when(gitHub.exchangeCode(eq("used"), anyString())).thenThrow(new OAuthCodeRejectedException("bad_verification_code"));

        mvc.perform(get("/api/auth/callback").param("code", "used").param("state", "s")
                        .cookie(new Cookie(AuthController.STATE_COOKIE, "s")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutRedirectsToFrontend() throws Exception {
        mvc.perform(get("/api/auth/logout"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost:3000"));
    }

    /** The main class's {@code @ConfigurationPropertiesScan} doesn't apply in a slice test. */
    @TestConfiguration
    @EnableConfigurationProperties({InsightsHubProperties.class, AuthProperties.class, RateLimitProperties.class})
    static class Properties {
        @Bean
        InMemoryRateLimiter rateLimiter() {
            return new InMemoryRateLimiter();
        }
    }

    private String bearer(long githubId) {
        return "Bearer " + jwtService.issue(githubId, "octocat").value();
    }
}
