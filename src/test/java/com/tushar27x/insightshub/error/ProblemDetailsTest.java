package com.tushar27x.insightshub.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailsTest {

    @Test
    void retryAfterRoundsUpToAtLeastOneSecond() {
        assertThat(ProblemDetails.retryAfterSeconds(Duration.ofMillis(359_400))).isEqualTo(360);
        assertThat(ProblemDetails.retryAfterSeconds(Duration.ofSeconds(17))).isEqualTo(17);
        assertThat(ProblemDetails.retryAfterSeconds(Duration.ZERO)).isEqualTo(1);
        assertThat(ProblemDetails.retryAfterSeconds(Duration.ofSeconds(-5))).isEqualTo(1);
    }

    @Test
    void writesTheContractShapeWithThePathButNotTheQuery() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/callback");
        request.setQueryString("code=one-time-code");
        ProblemDetail problem = ProblemDetails.of(HttpStatus.UNAUTHORIZED, "Log in again", request);
        problem.setProperty("errors", java.util.List.of());
        MockHttpServletResponse response = new MockHttpServletResponse();

        ProblemDetails.write(response, problem, JsonMapper.builder().build());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).isEqualTo("""
                {"type":"about:blank","title":"Unauthorized","status":401,"detail":"Log in again",\
                "instance":"/api/auth/callback","errors":[]}""");
    }

    @Test
    void invalidPathGivesNoInstance() {
        assertThat(ProblemDetails.instance("/a b")).isNull();
        assertThat(ProblemDetails.instance(null)).isNull();
    }
}
