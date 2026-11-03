package com.tushar27x.insightshub.config;

import com.tushar27x.insightshub.arena.ArenaController;
import com.tushar27x.insightshub.arena.ArenaService;
import com.tushar27x.insightshub.auth.AuthController;
import com.tushar27x.insightshub.auth.AuthProperties;
import com.tushar27x.insightshub.auth.AuthService;
import com.tushar27x.insightshub.notify.UnsubscribeController;
import com.tushar27x.insightshub.ratelimit.InMemoryRateLimiter;
import com.tushar27x.insightshub.ratelimit.RateLimitConfig;
import com.tushar27x.insightshub.ratelimit.RateLimitInterceptor;
import com.tushar27x.insightshub.ratelimit.RateLimitProperties;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.security.JwtConfig;
import com.tushar27x.insightshub.security.JwtService;
import com.tushar27x.insightshub.security.SecurityConfig;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.security.UnsubscribeTokens;
import com.tushar27x.insightshub.sync.SyncService;
import com.tushar27x.insightshub.user.Fixtures;
import com.tushar27x.insightshub.user.HeroClassService;
import com.tushar27x.insightshub.user.HeroController;
import com.tushar27x.insightshub.user.HeroQueryService;
import com.tushar27x.insightshub.user.InsightsQueryService;
import com.tushar27x.insightshub.user.PreferencesController;
import com.tushar27x.insightshub.user.PreferencesService;
import com.tushar27x.insightshub.user.UserController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B9: the OpenAPI document and Swagger UI, generated from every controller behind the real security chain and
 * rate-limit interceptor, with {@code application.yml}'s springdoc settings and the docs switched on. Offline: the
 * services are mocks and nothing is called. Checks that every contract endpoint is documented (and nothing else under
 * {@code /api}), that the schemas' keys are the fixtures' snake_case keys, and the shared errors and headers.
 * {@link OpenApiDocsOffTest} covers the default, docs off.
 */
@WebMvcTest(controllers = {AuthController.class, UserController.class, HeroController.class,
        PreferencesController.class, ArenaController.class, UnsubscribeController.class}, properties = {
        "insightshub.frontend-url=http://localhost:3000/",
        "insightshub.backend-url=http://localhost:8080",
        "insightshub.jwt-secret=" + SecurityTestData.JWT_SECRET,
        "insightshub.auth.github-client-id=client-id",
        "insightshub.auth.github-client-secret=client-secret",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
})
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocSecurityConfiguration.class, SpringDocSpecPropertiesConfiguration.class,
        SpringDocWebMvcConfiguration.class, SwaggerConfig.class, SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class})
@Import({SecurityConfig.class, JwtConfig.class, JwtService.class, ClockConfig.class,
        RateLimitInterceptor.class, RateLimitConfig.class, OpenApiConfig.class, OpenApiDocsTest.TestBeans.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiDocsTest {

    /** Every endpoint of {@code docs/api/contract.md}, as "METHOD path". */
    static final Set<String> CONTRACT = Set.of(
            "GET /api/auth/login", "GET /api/auth/callback", "GET /api/auth/logout",
            "GET /api/user/insights", "POST /api/user/sync",
            "GET /api/user/hero", "GET /api/user/hero/events", "PUT /api/user/hero/class",
            "GET /api/user/preferences", "PUT /api/user/preferences",
            "GET /api/leaderboard", "GET /api/user/rank",
            "GET /api/notifications/unsubscribe", "POST /api/notifications/unsubscribe");

    static final Set<String> PUBLIC = Set.of(
            "GET /api/auth/login", "GET /api/auth/callback", "GET /api/auth/logout",
            "GET /api/notifications/unsubscribe", "POST /api/notifications/unsubscribe");

    static final String PROBLEM_REF = "#/components/schemas/Problem";

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
    private JsonMapper jsonMapper;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private InsightsQueryService insightsQueryService;
    @MockitoBean
    private SyncService syncService;
    @MockitoBean
    private HeroQueryService heroQueryService;
    @MockitoBean
    private HeroClassService heroClassService;
    @MockitoBean
    private PreferencesService preferencesService;
    @MockitoBean
    private ArenaService arenaService;
    @MockitoBean
    private UnsubscribeTokens unsubscribeTokens;
    @MockitoBean
    private UsersRepository usersRepository;

    private JsonNode docs;

    /** Fetched once, without a JWT: the docs are public while switched on, and not rate-limited. */
    @BeforeAll
    void fetchDocs() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-RateLimit-Limit"))
                .andReturn().getResponse().getContentAsString();
        docs = jsonMapper.readTree(body);
    }

    @Test
    void documentsEveryContractEndpointAndNothingElse() {
        assertThat(operations().keySet()).containsExactlyInAnyOrderElementsOf(CONTRACT);
    }

    @Test
    void theApiNeedsTheBearerJwtExceptThePublicEndpoints() {
        JsonNode scheme = docs.at("/components/securitySchemes/" + OpenApiConfig.BEARER);
        assertThat(scheme.get("type").asString()).isEqualTo("http");
        assertThat(scheme.get("scheme").asString()).isEqualTo("bearer");
        assertThat(scheme.get("bearerFormat").asString()).isEqualTo("JWT");
        assertThat(docs.at("/security/0").has(OpenApiConfig.BEARER)).isTrue();

        operations().forEach((name, operation) -> {
            JsonNode security = operation.get("security");
            if (PUBLIC.contains(name)) {
                assertThat(security).as(name).isNotNull();
                assertThat(security.isEmpty()).as(name + " overrides the bearer requirement").isTrue();
                assertThat(operation.at("/responses/401").isMissingNode()).as(name).isTrue();
            } else {
                assertThat(security).as(name + " inherits the bearer requirement").isNull();
                assertProblem(name, operation, "401");
            }
        });
    }

    @Test
    void schemaKeysAreTheFixturesSnakeCaseKeys() {
        JsonNode hero = fixture("hero.json");
        assertKeys("HeroResponse", hero);
        assertKeys("HeroClass", hero.get("class"));
        assertKeys("HeroToday", hero.get("today"));
        assertKeys("HeroWeekDay", hero.get("week").get(0));

        JsonNode events = fixture("hero-events.json");
        assertKeys("HeroEventsResponse", events);
        assertKeys("HeroEvent", events.get("events").get(0));
        assertKeys("XpDay", events.get("xp_days").get(0));

        assertKeys("InsightsResponse", fixture("insights.json"));
        assertKeys("Preferences", fixture("preferences.json"));

        JsonNode leaderboard = fixture("leaderboard.json");
        assertKeys("LeaderboardResponse", leaderboard);
        assertKeys("LeaderboardEntry", leaderboard.get("entries").get(0));
        assertKeys("RankResponse", fixture("rank.json"));

        Set<String> problem = keys(fixture("rank-not-in-arena.json"));
        problem.add("errors"); // validation 400s only
        assertThat(keys(schema("Problem").get("properties"))).isEqualTo(problem);
    }

    @Test
    void responsesReferenceTheSchemasTheEndpointsReturn() {
        Map<String, String> returns = Map.of(
                "GET /api/user/insights", "InsightsResponse", "POST /api/user/sync", "InsightsResponse",
                "GET /api/user/hero", "HeroResponse", "GET /api/user/hero/events", "HeroEventsResponse",
                "PUT /api/user/hero/class", "HeroResponse", "GET /api/user/preferences", "Preferences",
                "PUT /api/user/preferences", "Preferences", "GET /api/leaderboard", "LeaderboardResponse",
                "GET /api/user/rank", "RankResponse");
        Map<String, JsonNode> operations = operations();
        returns.forEach((name, schema) -> assertThat(operations.get(name)
                .at("/responses/200/content/application~1json/schema/$ref").asString())
                .as(name).isEqualTo("#/components/schemas/" + schema));
        assertThat(operations.get("PUT /api/user/preferences")
                .at("/requestBody/content/application~1json/schema/$ref").asString())
                .isEqualTo("#/components/schemas/Preferences");
        JsonNode classBody = operations.get("PUT /api/user/hero/class").at("/requestBody/content/application~1json");
        assertThat(classBody.at("/schema/$ref").asString()).isEqualTo("#/components/schemas/ClassRequest");
        assertThat(classBody.get("examples").has("follow the top score")).isTrue();
        assertThat(texts(schema("Preferences").get("required")))
                .containsExactlyInAnyOrder("timezone", "rest_days", "alerts_enabled", "public_profile");
    }

    @Test
    void enumsAreTheApiValues() {
        JsonNode hero = schema("HeroResponse").get("properties");
        assertThat(texts(hero.at("/mood/enum")))
                .containsExactly("HAPPY", "OKAY", "WEAK", "HUNGRY", "SLEEPING", "LEVEL_UP", "FAINTED");
        assertThat(texts(hero.at("/tier/enum"))).containsExactly("CAMPFIRE", "VILLAGE", "KEEP", "CITADEL");
        assertThat(texts(hero.at("/rest_days/items/enum")))
                .containsExactly("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
        // @JsonValue: the week's statuses are lower case on the wire
        assertThat(texts(schema("HeroWeekDay").at("/properties/status/enum")))
                .containsExactly("active", "missed", "rest", "today", "future");
        assertThat(texts(schema("HeroClass").at("/properties/current/enum"))).containsExactly(
                "PALADIN", "BARBARIAN", "WIZARD", "ARTIFICER", "CLERIC", "BARD", "MONK", "ROGUE");
        assertThat(texts(schema("HeroClass").at("/properties/chosen/type"))).contains("null");
        assertThat(texts(schema("HeroClass").at("/properties/can_switch_at/type"))).contains("null");
    }

    @Test
    void everyErrorIsAProblemExceptTheUnsubscribePages() {
        operations().forEach((name, operation) -> operation.get("responses").properties().forEach(entry -> {
            int status = Integer.parseInt(entry.getKey());
            if (status >= 400 && !(name.contains("/unsubscribe") && status == 400)) {
                assertProblem(name, operation, entry.getKey());
            } else if (status >= 300 && status < 400) {
                assertThat(entry.getValue().get("content")).as(name + " " + status + " has no body").isNull();
            }
            assertThat(entry.getValue().get("description").asString()).as(name + " " + status).isNotBlank();
        }));
    }

    @Test
    void endpointSpecificStatusesFromTheContract() {
        Map<String, JsonNode> operations = operations();
        Map<String, List<String>> expected = Map.of(
                "GET /api/auth/login", List.of("302", "429"),
                "GET /api/auth/callback", List.of("302", "200", "400", "429", "502"),
                "GET /api/auth/logout", List.of("302"),
                "POST /api/user/sync", List.of("200", "404", "409", "429", "502"),
                "PUT /api/user/hero/class", List.of("200", "400", "404", "409", "429"),
                "GET /api/user/hero/events", List.of("200", "400", "404"),
                "PUT /api/user/preferences", List.of("200", "400", "404"),
                "GET /api/leaderboard", List.of("200", "400", "404"),
                "GET /api/user/rank", List.of("200", "404"));
        expected.forEach((name, statuses) -> assertThat(keys(operations.get(name).get("responses")))
                .as(name).containsAll(statuses));
        // login and logout only redirect
        assertThat(keys(operations.get("GET /api/auth/login").get("responses"))).doesNotContain("200");
        assertThat(keys(operations.get("GET /api/auth/logout").get("responses"))).doesNotContain("200");

        assertThat(operations.get("GET /api/auth/callback").at("/responses/302/headers").has("Location")).isTrue();
        assertThat(operations.get("GET /api/user/rank").at("/responses/404/description").asString())
                .contains("Not in the Arena");
        for (String name : List.of("GET /api/notifications/unsubscribe", "POST /api/notifications/unsubscribe")) {
            assertThat(operations.get(name).at("/responses/200/content").has("text/html")).as(name).isTrue();
            assertThat(operations.get(name).at("/responses/400/content").has("text/html")).as(name).isTrue();
        }
    }

    @Test
    void rateLimitedEndpointsDocumentTheirHeaders() {
        Map<String, JsonNode> operations = operations();
        for (String name : List.of("GET /api/user/hero", "PUT /api/user/hero/class", "GET /api/leaderboard",
                "GET /api/auth/login", "POST /api/notifications/unsubscribe")) {
            JsonNode responses = operations.get(name).get("responses");
            assertThat(responses.at("/429/headers").has("Retry-After")).as(name).isTrue();
            assertThat(responses.at("/429/headers").has("X-RateLimit-Limit")).as(name).isTrue();
            String success = responses.has("200") ? "200" : "302";
            assertThat(responses.at("/" + success + "/headers").has("X-RateLimit-Remaining")).as(name).isTrue();
        }
        // the sync's 429 is its cooldown, not the interceptor: Retry-After only
        JsonNode sync = operations.get("POST /api/user/sync").get("responses");
        assertThat(sync.at("/429/headers").has("Retry-After")).isTrue();
        assertThat(sync.at("/429/headers").has("X-RateLimit-Limit")).isFalse();
        assertThat(sync.at("/200/headers").isMissingNode()).isTrue();
    }

    @Test
    void swaggerUiAndTheYamlAreServedWithoutAJwt() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
    }

    @Test
    void theApiItselfStillNeedsTheJwt() throws Exception {
        mvc.perform(get("/api/user/hero")).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private Map<String, JsonNode> operations() {
        Map<String, JsonNode> operations = new TreeMap<>();
        docs.get("paths").properties().forEach(path -> path.getValue().properties().forEach(method ->
                operations.put(method.getKey().toUpperCase() + " " + path.getKey(), method.getValue())));
        return operations;
    }

    private JsonNode schema(String name) {
        JsonNode schema = docs.at("/components/schemas/" + name);
        assertThat(schema.isMissingNode()).as("schema " + name).isFalse();
        return schema;
    }

    private void assertKeys(String schema, JsonNode fixture) {
        assertThat(keys(schema(schema).get("properties"))).as(schema).isEqualTo(keys(fixture));
    }

    private static void assertProblem(String name, JsonNode operation, String status) {
        assertThat(operation.at("/responses/" + status + "/content/application~1problem+json/schema/$ref")
                .asString()).as(name + " " + status).isEqualTo(PROBLEM_REF);
    }

    private JsonNode fixture(String name) {
        return jsonMapper.readTree(Fixtures.read(name));
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        node.propertyNames().forEach(keys::add);
        return keys;
    }

    private static Set<String> texts(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        if (node.isArray()) {
            node.forEach(value -> values.add(value.asString()));
        } else if (!node.isMissingNode()) {
            values.add(node.asString());
        }
        return values;
    }
}
