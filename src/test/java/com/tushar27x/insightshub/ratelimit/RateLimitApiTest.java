package com.tushar27x.insightshub.ratelimit;

import com.tushar27x.insightshub.auth.AuthController;
import com.tushar27x.insightshub.auth.AuthProperties;
import com.tushar27x.insightshub.auth.AuthService;
import com.tushar27x.insightshub.config.ClockConfig;
import com.tushar27x.insightshub.config.InsightsHubProperties;
import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.security.JwtConfig;
import com.tushar27x.insightshub.security.JwtService;
import com.tushar27x.insightshub.security.SecurityConfig;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.sync.SyncService;
import com.tushar27x.insightshub.user.InsightsQueryService;
import com.tushar27x.insightshub.user.InsightsResponse;
import com.tushar27x.insightshub.user.UserController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real interceptor behind the real security chain, with an in-memory limiter instead of Redis.
 * Policies are small here (login 5/min, insights 3/min) to keep the tests short.
 */
@WebMvcTest(controllers = {AuthController.class, UserController.class}, properties = {
        "insightshub.frontend-url=http://localhost:3000/",
        "insightshub.backend-url=http://localhost:8080",
        "insightshub.jwt-secret=" + SecurityTestData.JWT_SECRET,
        "insightshub.auth.github-client-id=client-id",
        "insightshub.auth.github-client-secret=client-secret",
        "insightshub.rate-limit.policies.login.requests=5",
        "insightshub.rate-limit.policies.login.per=1m",
        "insightshub.rate-limit.policies.callback.requests=5",
        "insightshub.rate-limit.policies.callback.per=1m",
        "insightshub.rate-limit.policies.insights.requests=3",
        "insightshub.rate-limit.policies.insights.per=1m",
})
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class,
        RateLimitInterceptor.class, RateLimitConfig.class, RateLimitApiTest.TestBeans.class})
class RateLimitApiTest {

    @TestConfiguration
    @EnableConfigurationProperties({InsightsHubProperties.class, AuthProperties.class, RateLimitProperties.class})
    static class TestBeans {
        @Bean
        InMemoryRateLimiter rateLimiter() {
            return new InMemoryRateLimiter();
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private InMemoryRateLimiter limiter;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private SyncService syncService;
    @MockitoBean
    private InsightsQueryService insightsQueryService;

    @BeforeEach
    void setUp() {
        limiter.reset();
        when(authService.authorizeUri(anyString(), anyString())).thenReturn(URI.create("https://github.com/login/oauth/authorize"));
        when(insightsQueryService.getInsights(1L)).thenReturn(Optional.of(insights("one")));
        when(insightsQueryService.getInsights(2L)).thenReturn(Optional.of(insights("two")));
    }

    @Test
    void sixthLoginWithinAMinuteIs429WithRetryAfter() throws Exception {
        for (int i = 1; i <= 5; i++) {
            mvc.perform(login("203.0.113.7"))
                    .andExpect(status().isFound())
                    .andExpect(header().string(RateLimitInterceptor.REMAINING_HEADER, Integer.toString(5 - i)));
        }

        mvc.perform(login("203.0.113.7"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(header().string(RateLimitInterceptor.LIMIT_HEADER, "5"))
                .andExpect(header().string(RateLimitInterceptor.REMAINING_HEADER, "0"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Rate limit exceeded; try again in 60 s"));
    }

    @Test
    void eachIpHasItsOwnCounter() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("203.0.113.7"));
        }

        mvc.perform(login("198.51.100.20")).andExpect(status().isFound());
        mvc.perform(login("203.0.113.7")).andExpect(status().isTooManyRequests());
    }

    @Test
    void forwardedForHeaderDoesNotDodgeTheLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("203.0.113.7").header("X-Forwarded-For", "10.0.0." + i));
        }

        mvc.perform(login("203.0.113.7").header("X-Forwarded-For", "10.0.0.99"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void signedInEndpointsCountPerUserNotPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(insightsAs(1L)).andExpect(status().isOk());
        }

        mvc.perform(insightsAs(1L)).andExpect(status().isTooManyRequests());
        mvc.perform(insightsAs(2L)).andExpect(status().isOk()); // same IP, other user
        assertThat(limiter.keys).contains("insights:user:1", "insights:user:2");
    }

    @Test
    void badTokenIsRejectedBeforeItCounts() throws Exception {
        mvc.perform(get("/api/user/insights").header(HttpHeaders.AUTHORIZATION, "Bearer nope"))
                .andExpect(status().isUnauthorized());

        assertThat(limiter.keys).isEmpty();
    }

    @Test
    void endpointsWithoutRateLimitAreNotCounted() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/auth/logout")).andExpect(status().isFound());
        }

        assertThat(limiter.keys).isEmpty();
    }

    private MockHttpServletRequestBuilder login(String ip) {
        return get("/api/auth/login").with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    private MockHttpServletRequestBuilder insightsAs(long githubId) {
        return get("/api/user/insights")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(githubId, "user" + githubId).value());
    }

    private static InsightsResponse insights(String login) {
        return new InsightsResponse(login, Archetype.MONK, Map.of(), Map.of(), null, null, null, Map.of(), null,
                Instant.now());
    }
}
