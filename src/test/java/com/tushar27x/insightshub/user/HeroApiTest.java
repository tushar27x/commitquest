package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.auth.AuthProperties;
import com.tushar27x.insightshub.config.ClockConfig;
import com.tushar27x.insightshub.config.InsightsHubProperties;
import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.error.TooManyRequestsException;
import com.tushar27x.insightshub.ratelimit.InMemoryRateLimiter;
import com.tushar27x.insightshub.ratelimit.RateLimitConfig;
import com.tushar27x.insightshub.ratelimit.RateLimitInterceptor;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import com.tushar27x.insightshub.security.JwtConfig;
import com.tushar27x.insightshub.security.JwtService;
import com.tushar27x.insightshub.security.SecurityConfig;
import com.tushar27x.insightshub.security.SecurityTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/user/hero}, {@code /hero/events}, {@code /hero/class} and {@code /preferences} behind the real security chain and
 * rate-limit interceptor; the services are mocked. Payloads are compared strictly with the contract's fixtures,
 * so a renamed key, a wrong type or an enum in the wrong case fails here.
 */
@WebMvcTest(controllers = {HeroController.class, PreferencesController.class}, properties = {
        "insightshub.frontend-url=http://localhost:3000/",
        "insightshub.backend-url=http://localhost:8080",
        "insightshub.jwt-secret=" + SecurityTestData.JWT_SECRET,
        "insightshub.auth.github-client-id=client-id",
        "insightshub.auth.github-client-secret=client-secret",
        "insightshub.rate-limit.policies.hero.requests=1000",
        "insightshub.rate-limit.policies.hero.per=1m",
        "insightshub.rate-limit.policies.preferences.requests=1000",
        "insightshub.rate-limit.policies.preferences.per=1m",
        "insightshub.rate-limit.policies.hero-class.requests=1000",
        "insightshub.rate-limit.policies.hero-class.per=1m",
})
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class,
        RateLimitInterceptor.class, RateLimitConfig.class, HeroApiTest.TestBeans.class})
class HeroApiTest {

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
    private JsonMapper jsonMapper;
    @Autowired
    private InMemoryRateLimiter limiter;

    @MockitoBean
    private HeroQueryService heroQueryService;
    @MockitoBean
    private PreferencesService preferencesService;
    @MockitoBean
    private HeroClassService heroClassService;

    @BeforeEach
    void setUp() {
        limiter.reset();
    }

    // --- GET /hero ---

    @Test
    void heroIsServedInTheContractShape() throws Exception {
        String fixture = Fixtures.read("hero.json");
        when(heroQueryService.hero(ID)).thenReturn(Optional.of(jsonMapper.readValue(fixture, HeroResponse.class)));

        mvc.perform(as(get("/api/user/hero")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(fixture, JsonCompareMode.STRICT))
                .andExpect(header().string("X-RateLimit-Limit", "1000"));
    }

    @Test
    void cooldownVariantKeepsCanSwitchAtAsAnInstant() throws Exception {
        String fixture = Fixtures.read("hero.json");
        HeroResponse hero = jsonMapper.readValue(fixture, HeroResponse.class);
        HeroResponse cooldown = new HeroResponse(hero.level(), hero.xp(), hero.xpForNext(), hero.lives(), hero.maxLives(),
                hero.streak(), hero.streakForNextLife(), hero.mood(), hero.baseMood(), hero.tier(), hero.today(),
                hero.week(), hero.timezone(), hero.restDays(),
                new HeroResponse.HeroClass(com.tushar27x.insightshub.entity.Archetype.ROGUE,
                        com.tushar27x.insightshub.entity.Archetype.ROGUE, com.tushar27x.insightshub.entity.Archetype.MONK,
                        hero.heroClass().qualified(), java.time.Instant.parse("2026-10-21T09:00:00Z")),
                hero.lastEvaluatedOn());
        when(heroQueryService.hero(ID)).thenReturn(Optional.of(cooldown));

        mvc.perform(as(get("/api/user/hero")))
                .andExpect(jsonPath("$.class.chosen").value("ROGUE"))
                .andExpect(jsonPath("$.class.can_switch_at").value("2026-10-21T09:00:00Z"));
    }

    @Test
    void noHeroYetIs404Problem() throws Exception {
        when(heroQueryService.hero(ID)).thenReturn(Optional.empty());

        mvc.perform(as(get("/api/user/hero")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.instance").value("/api/user/hero"));
    }

    @Test
    void everyEndpointNeedsAToken() throws Exception {
        mvc.perform(get("/api/user/hero")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/user/hero/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/user/preferences")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/user/preferences").contentType(MediaType.APPLICATION_JSON).content(Fixtures.read("preferences.json")))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/user/hero/class").contentType(MediaType.APPLICATION_JSON).content("{\"archetype\": \"ROGUE\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(heroQueryService, preferencesService, heroClassService);
    }

    @Test
    void theUserComesFromTheTokenOnly() throws Exception {
        when(heroQueryService.hero(anyLong())).thenReturn(Optional.empty());

        mvc.perform(as(get("/api/user/hero").param("user_id", "1").param("githubId", "1")));

        verify(heroQueryService).hero(ID);
    }

    // --- GET /hero/events ---

    @Test
    void eventsAreServedInTheContractShapeForThirtyDaysByDefault() throws Exception {
        String fixture = Fixtures.read("hero-events.json");
        when(heroQueryService.events(ID, 30))
                .thenReturn(Optional.of(jsonMapper.readValue(fixture, HeroEventsResponse.class)));

        mvc.perform(as(get("/api/user/hero/events")))
                .andExpect(status().isOk())
                .andExpect(content().json(fixture, JsonCompareMode.STRICT));
    }

    @Test
    void daysMustBeOneToNinety() throws Exception {
        when(heroQueryService.events(eq(ID), anyInt())).thenReturn(Optional.of(new HeroEventsResponse(
                java.util.List.of(), java.util.List.of())));

        mvc.perform(as(get("/api/user/hero/events").param("days", "90"))).andExpect(status().isOk());
        mvc.perform(as(get("/api/user/hero/events").param("days", "1"))).andExpect(status().isOk());
        for (String days : new String[]{"0", "91", "-5"}) {
            mvc.perform(as(get("/api/user/hero/events").param("days", days)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("days"))
                    .andExpect(jsonPath("$.detail").value("The request is invalid; see 'errors'"));
        }
        mvc.perform(as(get("/api/user/hero/events").param("days", "lots"))).andExpect(status().isBadRequest());
    }

    @Test
    void eventsBeforeTheFirstSyncAre404() throws Exception {
        when(heroQueryService.events(ID, 30)).thenReturn(Optional.empty());

        mvc.perform(as(get("/api/user/hero/events"))).andExpect(status().isNotFound());
    }

    // --- PUT /hero/class ---

    @Test
    void aClassChangeReturnsTheHeroInTheContractShape() throws Exception {
        String fixture = Fixtures.read("hero.json");
        when(heroClassService.change(ID, Archetype.ROGUE))
                .thenReturn(Optional.of(jsonMapper.readValue(fixture, HeroResponse.class)));

        putClass("{\"archetype\": \"ROGUE\"}")
                .andExpect(status().isOk())
                .andExpect(content().json(fixture, JsonCompareMode.STRICT))
                .andExpect(header().string("X-RateLimit-Limit", "1000"));
    }

    @Test
    void nullOrNoKeyMeansFollowTheTopScore() throws Exception {
        when(heroClassService.change(ID, null)).thenReturn(Optional.of(
                jsonMapper.readValue(Fixtures.read("hero.json"), HeroResponse.class)));

        putClass("{\"archetype\": null}").andExpect(status().isOk());
        putClass("{}").andExpect(status().isOk());
        verify(heroClassService, org.mockito.Mockito.times(2)).change(ID, null);
    }

    @Test
    void notQualifiedIs409Problem() throws Exception {
        when(heroClassService.change(ID, Archetype.WIZARD)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "WIZARD isn't qualified: it scores 33, and a class needs 40 or the top score"));

        putClass("{\"archetype\": \"WIZARD\"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.detail").value("WIZARD isn't qualified: it scores 33, and a class needs 40 or the top score"))
                .andExpect(jsonPath("$.instance").value("/api/user/hero/class"));
    }

    @Test
    void theCooldownIs429WithRetryAfter() throws Exception {
        when(heroClassService.change(ID, Archetype.PALADIN)).thenThrow(new TooManyRequestsException(
                "The class was changed recently", Duration.ofHours(30)));

        putClass("{\"archetype\": \"PALADIN\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "108000"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void anUnknownClassIsSpringsOwn400() throws Exception {
        putClass("{\"archetype\": \"NECROMANCER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        putClass("{\"archetype\": \"rogue\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(heroClassService);
    }

    @Test
    void classChangeBeforeTheFirstSyncIs404() throws Exception {
        when(heroClassService.change(ID, Archetype.ROGUE)).thenReturn(Optional.empty());

        putClass("{\"archetype\": \"ROGUE\"}").andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions putClass(String body) throws Exception {
        return mvc.perform(as(put("/api/user/hero/class")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // --- /preferences ---

    @Test
    void preferencesAreServedInTheContractShape() throws Exception {
        String fixture = Fixtures.read("preferences.json");
        when(preferencesService.get(ID)).thenReturn(Optional.of(jsonMapper.readValue(fixture, Preferences.class)));

        mvc.perform(as(get("/api/user/preferences")))
                .andExpect(status().isOk())
                .andExpect(content().json(fixture, JsonCompareMode.STRICT));
    }

    @Test
    void validPreferencesAreSavedAndEchoed() throws Exception {
        String fixture = Fixtures.read("preferences.json");
        Preferences sent = jsonMapper.readValue(fixture, Preferences.class);
        when(preferencesService.update(ID, sent)).thenReturn(Optional.of(sent));

        mvc.perform(as(put("/api/user/preferences")).contentType(MediaType.APPLICATION_JSON).content(fixture))
                .andExpect(status().isOk())
                .andExpect(content().json(fixture, JsonCompareMode.STRICT));
        verify(preferencesService).update(ID, sent);
    }

    @Test
    void anOffsetIsNotAnIanaZone() throws Exception {
        for (String zone : new String[]{"+05:30", "GMT+5", "Mars/Olympus_Mons", ""}) {
            putPreferences("""
                    {"timezone": "%s", "rest_days": [], "alerts_enabled": true, "public_profile": false}
                    """.formatted(zone))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errors", hasSize(1)))
                    .andExpect(jsonPath("$.errors[0].field").value("timezone"))
                    .andExpect(jsonPath("$.errors[0].message").value("must be an IANA time zone, like Europe/Berlin"));
        }
        verifyNoInteractions(preferencesService);
    }

    @Test
    void atMostTwoRestDays() throws Exception {
        putPreferences("""
                {"timezone": "UTC", "rest_days": ["MONDAY", "SATURDAY", "SUNDAY"], "alerts_enabled": true, "public_profile": false}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rest_days"))
                .andExpect(jsonPath("$.errors[0].message").value("at most 2 rest days"));
    }

    @Test
    void noDuplicateRestDays() throws Exception {
        putPreferences("""
                {"timezone": "UTC", "rest_days": ["SUNDAY", "SUNDAY"], "alerts_enabled": true, "public_profile": false}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rest_days"))
                .andExpect(jsonPath("$.errors[0].message").value("must not repeat a day"));
    }

    @Test
    void everyFieldIsRequired() throws Exception {
        putPreferences("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "timezone", "rest_days", "alerts_enabled", "public_profile")));
        verifyNoInteractions(preferencesService);
    }

    @Test
    void anUnknownWeekdayIsSpringsOwn400() throws Exception {
        putPreferences("""
                {"timezone": "UTC", "rest_days": ["FUNDAY"], "alerts_enabled": true, "public_profile": false}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verify(preferencesService, org.mockito.Mockito.never()).update(anyLong(), any());
    }

    private org.springframework.test.web.servlet.ResultActions putPreferences(String body) throws Exception {
        return mvc.perform(as(put("/api/user/preferences")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(ID, "octocat").value());
    }
}
