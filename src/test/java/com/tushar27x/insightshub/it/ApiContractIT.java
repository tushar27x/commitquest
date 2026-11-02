package com.tushar27x.insightshub.it;

import com.tushar27x.insightshub.ai.AiInsightsService;
import com.tushar27x.insightshub.github.model.GitHubUser;
import com.tushar27x.insightshub.hero.HeroService;
import com.tushar27x.insightshub.insights.InsightsEngine;
import com.tushar27x.insightshub.insights.InsightsResult;
import com.tushar27x.insightshub.it.ContractShape.Rule;
import com.tushar27x.insightshub.it.ContractShape.Spec;
import com.tushar27x.insightshub.security.JwtService;
import com.tushar27x.insightshub.sync.InsightsWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static com.tushar27x.insightshub.it.ContractShape.ARCHETYPES;
import static com.tushar27x.insightshub.it.ContractShape.DATE;
import static com.tushar27x.insightshub.it.ContractShape.EVENT_TYPES;
import static com.tushar27x.insightshub.it.ContractShape.INSTANT;
import static com.tushar27x.insightshub.it.ContractShape.WEEKDAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * I3: the real API against {@code docs/api/contract.md} and its fixtures. Unlike the {@code @WebMvcTest} slices
 * (mocked services, strict values), this goes through everything: the security chain, the Redis rate limiter and
 * cache, the services, the SQL and JSONB on Postgres, and Jackson. Each response must have the fixture's shape
 * (keys, JSON types) and the contract's enums and formats; the values come from real data.
 * <p>
 * The hero lives through a 14-day story built so that the rules emit every event type B5 can produce, then
 * switches class ({@code PUT /hero/class}, B6) for {@code CLASS_CHANGED}.
 * <p>
 * B8: the Arena ({@code GET /leaderboard}, {@code GET /user/rank}) on the shared board, and {@code /user/rank}'s two
 * 404s, told apart by {@code title}.
 */
@IntegrationTest
class ApiContractIT {

    /** Block 9_000_4xx, see IntegrationTest. One user per test: rate limits and the cache are per user. */
    static final long BLOCK = 9_000_400L;

    static final Set<String> MOODS = Set.of("HAPPY", "OKAY", "WEAK", "HUNGRY", "SLEEPING", "LEVEL_UP", "FAINTED");
    static final Set<String> BASE_MOODS = Set.of("HAPPY", "OKAY", "WEAK");
    static final Set<String> TIERS = Set.of("CAMPFIRE", "VILLAGE", "KEEP", "CITADEL");
    static final Set<String> WEEK_STATUSES = Set.of("active", "missed", "rest", "today", "future");
    static final Set<String> PROBLEM_KEYS = Set.of("type", "title", "status", "detail", "instance");
    static final Pattern AVATAR_URL = Pattern.compile("https://avatars\\.githubusercontent\\.com/u/\\d+\\?v=4");

    @Autowired
    WebApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtService jwtService;
    @Autowired
    HeroService heroService;
    @Autowired
    InsightsEngine engine;
    @Autowired
    AiInsightsService ai;
    @Autowired
    InsightsWriter insightsWriter;
    @Autowired
    JsonMapper jsonMapper;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Built from the shared context: @AutoConfigureMockMvc would start a second one (and new containers).
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("DELETE FROM v2.users WHERE github_id BETWEEN ? AND ?", BLOCK, BLOCK + 99);
    }

    // ---- GET /insights ----

    @Test
    void insightsHasTheFixtureShape() throws Exception {
        long user = newUser(1, "it-contract-insights");
        saveInsights(user);

        MvcResult response = call(get("/api/user/insights"), user);
        assertJson(response, 200);
        assertRateLimitHeaders(response);
        JsonNode body = body(response);
        assertFits(body, "insights.json", new Spec(
                Set.of("$.character_sheet"),
                Set.of("$.repo_summaries"),
                Set.of(),
                List.of(Rule.oneOf("$.archetype", ARCHETYPES), Rule.format("$.updated_at", INSTANT))));
        assertThat(body.get("archetype_scores").propertyNames()).containsExactlyInAnyOrderElementsOf(ARCHETYPES);
        assertThat(body.at("/stats/heatmap_labels")).hasSize(7);
    }

    // ---- GET /hero, GET /hero/events ----

    @Test
    void heroAndEventsHaveTheFixtureShapeAndEveryEventType() throws Exception {
        long user = newUser(2, "it-contract-hero");
        playStory(user);

        MvcResult heroResponse = call(get("/api/user/hero"), user);
        assertJson(heroResponse, 200);
        assertRateLimitHeaders(heroResponse);
        JsonNode hero = body(heroResponse);
        assertFits(hero, "hero.json", heroSpec());
        assertThat(hero.get("week")).hasSize(7);
        assertThat(hero.get("max_lives").intValue()).isEqualTo(3);
        assertThat(hero.get("xp_for_next").intValue()).isEqualTo(50 * hero.get("level").intValue());

        MvcResult eventsResponse = call(get("/api/user/hero/events?days=30"), user);
        assertJson(eventsResponse, 200);
        assertRateLimitHeaders(eventsResponse);
        JsonNode events = body(eventsResponse);
        assertFits(events, "hero-events.json", new Spec(Set.of(), Set.of(), Set.of("$.events[].details"), List.of(
                Rule.oneOf("$.events[].type", EVENT_TYPES),
                Rule.format("$.events[].on_date", DATE),
                Rule.format("$.xp_days[].on_date", DATE))));

        // Every type B5 and B6 can emit came back, each with the details the contract defines for it.
        Set<String> types = new TreeSet<>();
        List<String> errors = new ArrayList<>();
        for (JsonNode event : events.get("events")) {
            String type = event.get("type").stringValue();
            types.add(type);
            errors.addAll(checkDetails(type, event.get("details")));
        }
        assertThat(errors).isEmpty();
        assertThat(types).containsExactlyInAnyOrder("LIFE_LOST", "LIFE_GAINED", "LEVEL_UP", "LEVEL_DOWN", "FAINTED",
                "RESTED", "CLASS_CHANGED");

        // Newest last; one xp_days row per settled day, ending at last_evaluated_on.
        List<String> eventDates = events.get("events").valueStream().map(e -> e.get("on_date").stringValue()).toList();
        assertThat(eventDates).isSorted();
        List<String> xpDates = events.get("xp_days").valueStream().map(d -> d.get("on_date").stringValue()).toList();
        assertThat(xpDates).isSorted().doesNotHaveDuplicates()
                .last().isEqualTo(hero.get("last_evaluated_on").stringValue());
    }

    @Test
    void heroEventsRejectsDaysOutsideTheRangeWithAFieldError() throws Exception {
        long user = newUser(3, "it-contract-days");
        heroService.seedIfMissing(user);

        MvcResult response = call(get("/api/user/hero/events?days=91"), user);
        JsonNode problem = assertProblem(response, 400, "/api/user/hero/events", true);
        assertThat(problem.get("errors").valueStream().map(e -> e.get("field").stringValue())).containsExactly("days");
    }

    // ---- PUT /hero/class ----

    @Test
    void classErrorsAreProblems() throws Exception {
        long user = newUser(7, "it-contract-class");
        saveInsights(user);
        heroService.seedIfMissing(user);
        JsonNode heroClass = body(call(get("/api/user/hero"), user)).get("class");
        Set<String> qualified = new TreeSet<>();
        heroClass.get("qualified").forEach(c -> qualified.add(c.stringValue()));

        String unqualified = ARCHETYPES.stream().filter(c -> !qualified.contains(c)).findFirst().orElseThrow();
        assertProblem(putClass(user, unqualified), 409, "/api/user/hero/class", false);

        assertJson(putClass(user, heroClass.get("scored").stringValue()), 200); // pins the top class
        MvcResult cooling = putClass(user, null);
        assertProblem(cooling, 429, "/api/user/hero/class", false);
        assertThat(cooling.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).matches("\\d+");

        MvcResult unknown = call(put("/api/user/hero/class").contentType(MediaType.APPLICATION_JSON)
                .content("{\"archetype\": \"NECROMANCER\"}"), user);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void classBeforeTheFirstSyncIs404() throws Exception {
        long user = newUser(8, "it-contract-class-new");

        assertProblem(putClass(user, "ROGUE"), 404, "/api/user/hero/class", false);
    }

    // ---- GET/PUT /preferences ----

    @Test
    void preferencesHaveTheFixtureShapeBothWays() throws Exception {
        long user = newUser(4, "it-contract-prefs");
        Spec spec = new Spec(Set.of(), Set.of(), Set.of(), List.of(Rule.oneOf("$.rest_days[]", WEEKDAYS)));

        MvcResult got = call(get("/api/user/preferences"), user);
        assertJson(got, 200);
        assertRateLimitHeaders(got);
        assertFits(body(got), "preferences.json", spec);

        // The fixture itself is a valid PUT body, and the answer has the same shape.
        String fixture = ContractShape.fixture("preferences.json").toString();
        MvcResult saved = call(put("/api/user/preferences").contentType(MediaType.APPLICATION_JSON).content(fixture), user);
        assertJson(saved, 200);
        assertFits(body(saved), "preferences.json", spec);
        assertThat(body(saved)).isEqualTo(ContractShape.fixture("preferences.json"));
    }

    @Test
    void invalidPreferencesAreAValidationProblemWithSnakeCaseFields() throws Exception {
        long user = newUser(5, "it-contract-prefs-400");
        String bad = """
                {"timezone": "+05:30", "rest_days": ["MONDAY", "TUESDAY", "WEDNESDAY"]}""";

        MvcResult response = call(put("/api/user/preferences").contentType(MediaType.APPLICATION_JSON).content(bad), user);
        JsonNode problem = assertProblem(response, 400, "/api/user/preferences", true);
        assertThat(problem.get("detail").stringValue()).isEqualTo("The request is invalid; see 'errors'");
        assertThat(problem.get("errors").valueStream().map(e -> e.get("field").stringValue()).distinct())
                .containsExactlyInAnyOrder("timezone", "rest_days", "alerts_enabled", "public_profile");
        assertThat(problem.get("errors")).allSatisfy(e -> {
            assertThat(e.propertyNames()).containsExactlyInAnyOrder("field", "message");
            assertThat(e.get("message").stringValue()).isNotBlank();
        });
    }

    // ---- GET /leaderboard, GET /user/rank (B8) ----

    @Test
    void rankAndLeaderboardHaveTheFixtureShape() throws Exception {
        long user = newUser(9, "it-contract-arena");
        saveInsights(user);
        heroService.seedIfMissing(user);
        jdbc.update("UPDATE v2.users SET public_profile = true WHERE github_id = ?", user);
        String current = body(call(get("/api/user/hero"), user)).at("/class/current").stringValue();

        MvcResult rankResponse = call(get("/api/user/rank"), user);
        assertJson(rankResponse, 200);
        assertRateLimitHeaders(rankResponse);
        JsonNode rank = body(rankResponse);
        assertFits(rank, "rank.json", new Spec(Set.of(), Set.of(), Set.of(), List.of(Rule.oneOf("$.archetype", ARCHETYPES))));
        assertThat(rank.get("archetype").stringValue()).isEqualTo(current);
        int mine = rank.get("rank").intValue();
        int total = rank.get("total").intValue();
        assertThat(mine).isBetween(1, total);
        assertThat(rank.get("top_percent").intValue()).isEqualTo((100 * mine + total - 1) / total).isBetween(1, 100);

        // The caller's page, as the contract tells the UI to find it; no archetype = the caller's current class.
        int page = (mine - 1) / 20;
        MvcResult boardResponse = call(get("/api/leaderboard?page=" + page), user);
        assertJson(boardResponse, 200);
        assertRateLimitHeaders(boardResponse);
        JsonNode board = body(boardResponse);
        assertFits(board, "leaderboard.json", leaderboardSpec());
        assertThat(board.get("archetype").stringValue()).isEqualTo(current);
        assertThat(board.get("page").intValue()).isEqualTo(page);
        assertThat(board.get("size").intValue()).isEqualTo(20);
        assertThat(board.get("total").intValue()).isEqualTo(total);
        List<JsonNode> you = board.get("entries").valueStream().filter(e -> e.get("is_you").booleanValue()).toList();
        assertThat(you).singleElement().satisfies(e -> {
            assertThat(e.get("rank").intValue()).isEqualTo(mine);
            assertThat(e.get("login").stringValue()).isEqualTo("it-contract-arena");
            assertThat(e.get("avatar_url").stringValue()).isEqualTo("https://avatars.githubusercontent.com/u/" + user + "?v=4");
        });

        // Any class's board can be read; past the last page, entries is [].
        String other = ARCHETYPES.stream().filter(c -> !c.equals(current)).findFirst().orElseThrow();
        JsonNode otherBoard = body(call(get("/api/leaderboard?archetype=" + other + "&size=50"), user));
        assertThat(otherBoard.get("archetype").stringValue()).isEqualTo(other);
        assertThat(otherBoard.get("entries").valueStream().noneMatch(e -> e.get("is_you").booleanValue())).isTrue();
        JsonNode past = body(call(get("/api/leaderboard?page=" + (total / 20 + 1)), user));
        assertThat(past.get("entries").isArray()).isTrue();
        assertThat(past.get("entries")).isEmpty();
    }

    @Test
    void leaderboardRejectsPageAndSizeOutsideTheRangeWithAFieldError() throws Exception {
        long user = newUser(10, "it-contract-arena-400");

        JsonNode size = assertProblem(call(get("/api/leaderboard?archetype=MONK&size=51"), user), 400,
                "/api/leaderboard", true);
        assertThat(size.get("errors").valueStream().map(e -> e.get("field").stringValue())).containsExactly("size");
        JsonNode page = assertProblem(call(get("/api/leaderboard?archetype=MONK&page=-1"), user), 400,
                "/api/leaderboard", true);
        assertThat(page.get("errors").valueStream().map(e -> e.get("field").stringValue())).containsExactly("page");
        assertProblem(call(get("/api/leaderboard?archetype=monk"), user), 400, "/api/leaderboard", false);
    }

    @Test
    void aPrivateHeroIsNotInTheArena() throws Exception {
        long user = newUser(11, "it-contract-arena-private"); // public_profile starts off
        saveInsights(user);
        heroService.seedIfMissing(user);

        MvcResult response = call(get("/api/user/rank"), user);
        JsonNode problem = assertProblem(response, 404, "/api/user/rank", false);
        assertThat(problem).isEqualTo(ContractShape.fixture("rank-not-in-arena.json"));
        // Its own board can still be read, without it.
        JsonNode board = body(call(get("/api/leaderboard"), user));
        assertThat(board.get("entries").valueStream().noneMatch(e -> e.get("is_you").booleanValue())).isTrue();
    }

    @Test
    void beforeTheFirstSyncTheArenaIsThePlain404() throws Exception {
        long user = newUser(12, "it-contract-arena-new");
        jdbc.update("UPDATE v2.users SET public_profile = true WHERE github_id = ?", user);

        JsonNode rank = assertProblem(call(get("/api/user/rank"), user), 404, "/api/user/rank", false);
        assertThat(rank.get("title").stringValue()).isEqualTo("Not Found");
        JsonNode board = assertProblem(call(get("/api/leaderboard"), user), 404, "/api/leaderboard", false);
        assertThat(board.get("title").stringValue()).isEqualTo("Not Found");
        // With a class, any board can be read before the first sync.
        assertJson(call(get("/api/leaderboard?archetype=MONK"), user), 200);
    }

    // ---- Errors every screen handles ----

    @Test
    void beforeTheFirstSyncEveryUserEndpointIs404() throws Exception {
        long user = newUser(6, "it-contract-new");

        for (String path : List.of("/api/user/insights", "/api/user/hero", "/api/user/hero/events")) {
            assertProblem(call(get(path), user), 404, path, false);
        }
    }

    @Test
    void withoutATokenEveryUserEndpointIs401() throws Exception {
        for (String path : List.of("/api/user/insights", "/api/user/hero", "/api/user/hero/events",
                "/api/user/preferences", "/api/leaderboard", "/api/user/rank")) {
            assertProblem(mvc.perform(get(path)).andReturn(), 401, path, false);
        }
    }

    // ---- The story ----

    /**
     * Settles 14 days with the real rules on the real tables. With d = today − n (UTC, the user's zone):
     * d14 inactive → LIFE_LOST (2 hearts) · d13–d9 active → LIFE_GAINED on d11 (3-day streak), LEVEL_UP on d9
     * (50 XP) · d8 rest day → RESTED · d7–d5 inactive → LIFE_LOST ×3, LEVEL_DOWN on d5 · d4–d2 inactive →
     * LIFE_LOST ×3, FAINTED on d2 (level 1) · d1 (rest weekday again) settles only after 06:00 UTC. Then
     * today: PUT /hero/class to a qualified class other than the top one (or pins the top one) → CLASS_CHANGED.
     */
    private void playStory(long user) throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        saveInsights(user); // GET /hero needs them too: the first sync writes the insights, then seeds the hero
        assertThat(heroService.seedIfMissing(user)).isTrue(); // no activity yet: level 1, 0 XP, 3 hearts
        jdbc.update("UPDATE v2.hero_state SET last_evaluated_on = ? WHERE user_id = ?", today.minusDays(15), user);
        for (int n = 13; n >= 9; n--) {
            jdbc.update("INSERT INTO v2.daily_contributions (user_id, date, calendar_count) VALUES (?, ?, 1)",
                    user, today.minusDays(n));
        }
        int restBit = 1 << (today.minusDays(8).getDayOfWeek().getValue() - 1);
        jdbc.update("UPDATE v2.users SET rest_days = ? WHERE github_id = ?", restBit, user);

        assertThat(heroService.evaluate(user)).isGreaterThanOrEqualTo(13);

        JsonNode heroClass = body(call(get("/api/user/hero"), user)).get("class");
        String scored = heroClass.get("scored").stringValue();
        String target = heroClass.get("qualified").valueStream().map(JsonNode::stringValue)
                .filter(c -> !c.equals(scored)).findFirst().orElse(scored);
        MvcResult switched = putClass(user, target);
        assertJson(switched, 200);
        assertRateLimitHeaders(switched);
        JsonNode hero = body(switched); // the same object as GET /hero
        assertFits(hero, "hero.json", heroSpec());
        assertThat(hero.at("/class/chosen").stringValue()).isEqualTo(target);
        assertThat(hero.at("/class/can_switch_at").isString()).isTrue();
    }

    /** @param archetype null sends {@code {"archetype": null}} */
    private MvcResult putClass(long user, String archetype) throws Exception {
        String body = archetype == null ? "{\"archetype\": null}" : "{\"archetype\": \"" + archetype + "\"}";
        return call(put("/api/user/hero/class").contentType(MediaType.APPLICATION_JSON).content(body), user);
    }

    // ---- Helpers ----

    private Spec heroSpec() {
        return new Spec(
                Set.of("$.class.chosen", "$.class.can_switch_at"),
                Set.of(), Set.of(),
                List.of(Rule.oneOf("$.mood", MOODS), Rule.oneOf("$.base_mood", BASE_MOODS), Rule.oneOf("$.tier", TIERS),
                        Rule.oneOf("$.week[].status", WEEK_STATUSES), Rule.format("$.week[].date", DATE),
                        Rule.format("$.today.date", DATE), Rule.format("$.today.day_ends_at", INSTANT),
                        Rule.format("$.last_evaluated_on", DATE), Rule.oneOf("$.rest_days[]", WEEKDAYS),
                        Rule.oneOf("$.class.chosen", ARCHETYPES), Rule.oneOf("$.class.current", ARCHETYPES),
                        Rule.oneOf("$.class.scored", ARCHETYPES), Rule.oneOf("$.class.qualified[]", ARCHETYPES),
                        Rule.format("$.class.can_switch_at", INSTANT)));
    }

    private static Spec leaderboardSpec() {
        return new Spec(Set.of(), Set.of(), Set.of(), List.of(Rule.oneOf("$.archetype", ARCHETYPES),
                Rule.oneOf("$.entries[].tier", TIERS), Rule.format("$.entries[].avatar_url", AVATAR_URL)));
    }

    /** The contract's {@code details} table, per event type. */
    private static List<String> checkDetails(String type, JsonNode details) {
        Set<String> expected = switch (type) {
            case "LIFE_LOST" -> Set.of("lives", "reason");
            case "LIFE_GAINED" -> Set.of("lives", "streak");
            case "LEVEL_UP", "LEVEL_DOWN" -> Set.of("from", "to");
            case "CLASS_CHANGED" -> Set.of("from", "to", "chosen");
            default -> Set.of();
        };
        List<String> errors = new ArrayList<>();
        if (!details.isObject() || !new TreeSet<>(details.propertyNames()).equals(new TreeSet<>(expected))) {
            errors.add(type + ": details " + details + ", the contract has keys " + new TreeSet<>(expected));
            return errors;
        }
        if (type.equals("CLASS_CHANGED")) {
            for (String key : List.of("from", "to")) {
                if (!details.get(key).isString() || !ARCHETYPES.contains(details.get(key).stringValue())) {
                    errors.add("CLASS_CHANGED." + key + ": not a class: " + details.get(key));
                }
            }
            JsonNode chosen = details.get("chosen");
            if (!chosen.isNull() && !(chosen.isString() && ARCHETYPES.contains(chosen.stringValue()))) {
                errors.add("CLASS_CHANGED.chosen: neither null nor a class: " + chosen);
            }
            return errors;
        }
        for (String key : List.of("lives", "streak", "from", "to")) {
            if (expected.contains(key) && !details.get(key).isIntegralNumber()) {
                errors.add(type + "." + key + ": not an integer: " + details.get(key));
            }
        }
        if (type.equals("LIFE_LOST") && !"NO_ACTIVITY".equals(details.get("reason").stringValue())) {
            errors.add("LIFE_LOST.reason: " + details.get("reason"));
        }
        return errors;
    }

    /**
     * The sync's write path minus GitHub: the v1 parity user through the engine, the AI layer (its static
     * fallbacks: the LLM port is closed in ITs) and the writer, as {@code SyncService.sync} does it.
     */
    private void saveInsights(long user) throws IOException {
        GitHubUser gitHubUser = readResource("insights/v1-parity-user.json", GitHubUser.class);
        InsightsResult result = engine.calculate(gitHubUser);
        Map<String, Object> stats = jsonMapper.convertValue(result.stats(), new TypeReference<>() {
        });
        insightsWriter.save(user, stats, result, ai.generate("it-user-" + user, result), Instant.now());
    }

    private long newUser(int n, String login) {
        long id = BLOCK + n;
        jdbc.update("INSERT INTO v2.users (github_id, login, created_at) VALUES (?, ?, now() - interval '60 days')",
                id, login);
        return id;
    }

    private MvcResult call(MockHttpServletRequestBuilder request, long user) throws Exception {
        String token = jwtService.issue(user, "it-user-" + user).value();
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return ContractShape.parse(result.getResponse().getContentAsString());
    }

    private static void assertJson(MvcResult result, int status) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(MediaType.parseMediaType(result.getResponse().getContentType()).isCompatibleWith(MediaType.APPLICATION_JSON))
                .isTrue();
    }

    private static void assertRateLimitHeaders(MvcResult result) {
        assertThat(result.getResponse().getHeader("X-RateLimit-Limit")).matches("\\d+");
        assertThat(result.getResponse().getHeader("X-RateLimit-Remaining")).matches("\\d+");
    }

    /** The contract's error shape: problem+json, all five keys, {@code about:blank}, the path as instance. */
    private static JsonNode assertProblem(MvcResult result, int status, String path, boolean validation)
            throws Exception {
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(status);
        assertThat(MediaType.parseMediaType(result.getResponse().getContentType()))
                .as(path).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode problem = body(result);
        Set<String> keys = new TreeSet<>(PROBLEM_KEYS);
        if (validation) {
            keys.add("errors");
        }
        assertThat(problem.propertyNames()).as(path).containsExactlyInAnyOrderElementsOf(keys);
        assertThat(problem.get("type").stringValue()).isEqualTo("about:blank");
        assertThat(problem.get("status").intValue()).isEqualTo(status);
        assertThat(problem.get("instance").stringValue()).isEqualTo(path);
        return problem;
    }

    private static void assertFits(JsonNode actual, String fixture, Spec spec) {
        assertThat(ContractShape.check(actual, ContractShape.fixture(fixture), spec)).as(fixture).isEmpty();
    }

    private <T> T readResource(String path, Class<T> type) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return jsonMapper.readValue(in, type);
        }
    }
}
