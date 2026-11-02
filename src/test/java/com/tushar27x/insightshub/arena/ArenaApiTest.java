package com.tushar27x.insightshub.arena;

import com.tushar27x.insightshub.auth.AuthProperties;
import com.tushar27x.insightshub.config.ClockConfig;
import com.tushar27x.insightshub.config.InsightsHubProperties;
import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.ratelimit.InMemoryRateLimiter;
import com.tushar27x.insightshub.ratelimit.RateLimitConfig;
import com.tushar27x.insightshub.ratelimit.RateLimitInterceptor;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import com.tushar27x.insightshub.security.JwtConfig;
import com.tushar27x.insightshub.security.JwtService;
import com.tushar27x.insightshub.security.SecurityConfig;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.user.Fixtures;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/leaderboard} and {@code GET /api/user/rank} behind the real security chain and rate-limit
 * interceptor; the service is mocked. Payloads and the "Not in the Arena" problem are compared strictly with the
 * contract's fixtures ({@code leaderboard.json}, {@code rank.json}, {@code rank-not-in-arena.json}).
 */
@WebMvcTest(controllers = ArenaController.class, properties = {
        "insightshub.frontend-url=http://localhost:3000/",
        "insightshub.backend-url=http://localhost:8080",
        "insightshub.jwt-secret=" + SecurityTestData.JWT_SECRET,
        "insightshub.auth.github-client-id=client-id",
        "insightshub.auth.github-client-secret=client-secret",
        "insightshub.rate-limit.policies.arena.requests=1000",
        "insightshub.rate-limit.policies.arena.per=1m",
})
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class,
        RateLimitInterceptor.class, RateLimitConfig.class, ArenaApiTest.TestBeans.class})
class ArenaApiTest {

    private static final long ID = 583231L;

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
    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private ArenaService arenaService;

    @BeforeEach
    void setUp() {
        limiter.reset();
    }

    // --- GET /api/leaderboard ---

    @Test
    void theLeaderboardIsServedInTheContractShape() throws Exception {
        String fixture = Fixtures.read("leaderboard.json");
        when(arenaService.leaderboard(ID, Archetype.MONK, 0, Arena.DEFAULT_PAGE_SIZE))
                .thenReturn(Optional.of(jsonMapper.readValue(fixture, LeaderboardResponse.class)));

        mvc.perform(as(get("/api/leaderboard").param("archetype", "MONK")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-RateLimit-Limit", "1000"))
                .andExpect(content().json(fixture, JsonCompareMode.STRICT));
    }

    @Test
    void withoutParametersItIsTheCallersClassFirstPageOfTwenty() throws Exception {
        when(arenaService.leaderboard(eq(ID), isNull(), eq(0), eq(Arena.DEFAULT_PAGE_SIZE)))
                .thenReturn(Optional.of(new LeaderboardResponse(Archetype.ROGUE, 0, 20, 0, List.of())));

        mvc.perform(as(get("/api/leaderboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archetype").value("ROGUE"))
                .andExpect(jsonPath("$.entries").isEmpty());
    }

    @Test
    void pageAndSizeAreValidated() throws Exception {
        when(arenaService.leaderboard(eq(ID), any(), anyInt(), anyInt()))
                .thenReturn(Optional.of(new LeaderboardResponse(Archetype.ROGUE, 0, 1, 0, List.of())));

        mvc.perform(as(get("/api/leaderboard").param("size", "1"))).andExpect(status().isOk());
        mvc.perform(as(get("/api/leaderboard").param("size", "" + Arena.MAX_PAGE_SIZE).param("page", "3")))
                .andExpect(status().isOk());
        for (String size : new String[]{"0", "" + (Arena.MAX_PAGE_SIZE + 1), "-1"}) {
            mvc.perform(as(get("/api/leaderboard").param("size", size)))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .andExpect(jsonPath("$.errors[0].field").value("size"))
                    .andExpect(jsonPath("$.detail").value("The request is invalid; see 'errors'"));
        }
        mvc.perform(as(get("/api/leaderboard").param("page", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
        mvc.perform(as(get("/api/leaderboard").param("page", "first"))).andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownClassIsSpringsOwn400() throws Exception {
        for (String archetype : new String[]{"NECROMANCER", "monk"}) {
            mvc.perform(as(get("/api/leaderboard").param("archetype", archetype)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }
        verifyNoInteractions(arenaService);
    }

    @Test
    void noClassAndNoHeroIs404Problem() throws Exception {
        when(arenaService.leaderboard(eq(ID), isNull(), anyInt(), anyInt())).thenReturn(Optional.empty());

        mvc.perform(as(get("/api/leaderboard")))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.instance").value("/api/leaderboard"));
    }

    // --- GET /api/user/rank ---

    @Test
    void theRankIsServedInTheContractShape() throws Exception {
        String fixture = Fixtures.read("rank.json");
        when(arenaService.rank(ID)).thenReturn(Optional.of(jsonMapper.readValue(fixture, RankResponse.class)));

        mvc.perform(as(get("/api/user/rank")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-RateLimit-Limit", "1000"))
                .andExpect(content().json(fixture, JsonCompareMode.STRICT));
    }

    @Test
    void aCallerOffTheBoardIsA404NotInTheArena() throws Exception {
        when(arenaService.rank(ID)).thenThrow(new NotInArenaException());

        mvc.perform(as(get("/api/user/rank")))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(content().json(Fixtures.read("rank-not-in-arena.json"), JsonCompareMode.STRICT));
    }

    @Test
    void noRankBeforeTheFirstSyncIsThePlain404() throws Exception {
        when(arenaService.rank(ID)).thenReturn(Optional.empty());

        mvc.perform(as(get("/api/user/rank")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("No hero yet for this user; sync first"))
                .andExpect(jsonPath("$.instance").value("/api/user/rank"));
    }

    // --- both ---

    @Test
    void bothNeedAToken() throws Exception {
        mvc.perform(get("/api/leaderboard").param("archetype", "MONK")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/user/rank")).andExpect(status().isUnauthorized());
        verifyNoInteractions(arenaService);
    }

    @Test
    void theCallerComesFromTheTokenOnly() throws Exception {
        when(arenaService.rank(anyLong())).thenReturn(Optional.empty());

        mvc.perform(as(get("/api/user/rank").param("user_id", "1").param("githubId", "1")));

        verify(arenaService).rank(ID);
    }

    @Test
    void bothShareOnePolicy() throws Exception {
        when(arenaService.rank(ID)).thenReturn(Optional.empty());
        when(arenaService.leaderboard(eq(ID), any(), anyInt(), anyInt())).thenReturn(Optional.empty());

        mvc.perform(as(get("/api/user/rank"))).andExpect(header().string("X-RateLimit-Remaining", "999"));
        mvc.perform(as(get("/api/leaderboard"))).andExpect(header().string("X-RateLimit-Remaining", "998"));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(ID, "octocat").value());
    }
}
