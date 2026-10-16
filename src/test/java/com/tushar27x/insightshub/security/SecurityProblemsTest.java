package com.tushar27x.insightshub.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** The 401/403 writers on their own; {@code AuthApiTest} covers 401 through the real filter chain. */
class SecurityProblemsTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/hero");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void unauthorizedKeepsTheBearerChallengeAndHidesTheReason() throws Exception {
        SecurityConfig.unauthorized(jsonMapper)
                .commence(request, response, new BadCredentialsException("Jwt expired at 2026-10-15T10:00:00Z"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"detail\":\"" + SecurityConfig.UNAUTHORIZED + "\"", "\"instance\":\"/api/user/hero\"")
                .doesNotContain("expired at");
    }

    @Test
    void forbiddenIsAProblemToo() throws Exception {
        SecurityConfig.forbidden(jsonMapper).handle(request, response, new AccessDeniedException("scope"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(response.getContentAsString())
                .contains("\"status\":403", "\"detail\":\"" + SecurityConfig.FORBIDDEN + "\"");
    }
}
