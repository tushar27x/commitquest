package com.tushar27x.insightshub.error;

import com.tushar27x.insightshub.github.GitHubApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The handler on its own, with a controller that throws each kind of error. No security, no context. */
class ApiExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void gitHubFailureIs502WithoutUpstreamDetails() throws Exception {
        expectProblem(mvc.perform(get("/test/github")), HttpStatus.BAD_GATEWAY, "/test/github")
                .andExpect(jsonPath("$.detail").value("GitHub is unavailable; try again later"))
                .andExpect(content().string(not(containsString("octocat"))));
    }

    @Test
    void unexpectedFailureIs500AndNeverShowsTheMessage() throws Exception {
        expectProblem(mvc.perform(get("/test/crash")), HttpStatus.INTERNAL_SERVER_ERROR, "/test/crash")
                .andExpect(jsonPath("$.detail").value("Something went wrong on our side; try again later"))
                .andExpect(content().string(not(containsString("relation"))));
    }

    @Test
    void responseStatusExceptionKeepsItsStatusAndDetail() throws Exception {
        expectProblem(mvc.perform(get("/test/missing")), HttpStatus.NOT_FOUND, "/test/missing")
                .andExpect(jsonPath("$.detail").value("No insights yet for this user"));
    }

    @Test
    void tooManyRequestsCarriesRetryAfterRoundedUp() throws Exception {
        expectProblem(mvc.perform(get("/test/cooldown")), HttpStatus.TOO_MANY_REQUESTS, "/test/cooldown")
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "360"))
                .andExpect(jsonPath("$.detail").value("You synced recently; try again later"));
    }

    @Test
    void invalidBodyIs400WithEveryFieldError() throws Exception {
        expectProblem(mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\": \"\", \"restDays\": 9}")), HttpStatus.BAD_REQUEST, "/test/body")
                .andExpect(jsonPath("$.detail").value(ApiExceptionHandler.INVALID_REQUEST))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("timezone", "rest_days")))
                .andExpect(jsonPath("$.errors[?(@.field == 'rest_days')].message").value("must be less than or equal to 2"));
    }

    @Test
    void invalidRequestParamIs400WithItsName() throws Exception {
        expectProblem(mvc.perform(get("/test/param").param("code", "x".repeat(11))), HttpStatus.BAD_REQUEST, "/test/param")
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[0].message").value("size must be between 0 and 10"));
    }

    @Test
    void malformedJsonAndMissingParamsAre400Problems() throws Exception {
        expectProblem(mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{")),
                HttpStatus.BAD_REQUEST, "/test/body");
        expectProblem(mvc.perform(get("/test/param")), HttpStatus.BAD_REQUEST, "/test/param");
    }

    @Test
    void wrongMethodIs405Problem() throws Exception {
        expectProblem(mvc.perform(delete("/test/github")), HttpStatus.METHOD_NOT_ALLOWED, "/test/github");
    }

    @Test
    void fieldNamesAreSnakeCaseLikeTheApi() {
        assertThat(ApiExceptionHandler.jsonName("restDays")).isEqualTo("rest_days");
        assertThat(ApiExceptionHandler.jsonName("timezone")).isEqualTo("timezone");
        assertThat(ApiExceptionHandler.jsonName("week[0].restDays")).isEqualTo("week[0].rest_days");
    }

    private static ResultActions expectProblem(ResultActions result, HttpStatus status, String path) throws Exception {
        return result.andExpect(status().is(status.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value(status.getReasonPhrase()))
                .andExpect(jsonPath("$.status").value(status.value()))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value(path));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/github")
        String github() {
            throw new GitHubApiException("GitHub GraphQL errors for 'octocat': HTTP 503");
        }

        @GetMapping("/test/crash")
        String crash() {
            throw new IllegalStateException("ERROR: relation \"v2.users\" does not exist");
        }

        @GetMapping("/test/missing")
        String missing() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No insights yet for this user");
        }

        @GetMapping("/test/cooldown")
        String cooldown() {
            throw new TooManyRequestsException("You synced recently; try again later", Duration.ofMillis(359_400));
        }

        @PostMapping("/test/body")
        String body(@Valid @RequestBody Preferences preferences) {
            return "ok";
        }

        @GetMapping("/test/param")
        String param(@RequestParam @Size(max = 10) String code) {
            return code;
        }
    }

    // camelCase on purpose: the standalone setup's JSON mapper has no snake_case naming
    record Preferences(@NotBlank String timezone, @Max(2) int restDays) {
    }
}
