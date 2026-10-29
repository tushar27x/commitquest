package com.tushar27x.insightshub.it;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * GitHub for the e2e backend ({@link E2eBackend}), answered in-process: every request the app's
 * {@code RestClient}s send to {@code github.com} or {@code api.github.com} gets a canned answer, and
 * nothing leaves the machine. The browser's trip to GitHub's authorize page is stubbed by Playwright.
 * <ul>
 *   <li>{@code POST github.com/login/oauth/access_token}: a token generated at runtime (B31).</li>
 *   <li>{@code GET /user}, {@code GET /user/emails}: the octocat of the GraphQL fixtures.</li>
 *   <li>{@code POST /graphql}: {@code github/branch-activity-response.json} for the branch scan (the
 *       request with a {@code userId} variable),
 *       {@code github/user-insights-response.json} for everything else.</li>
 * </ul>
 * Any other GitHub call gets a 404, so a new one shows up as a failure instead of a silent pass.
 */
@TestConfiguration(proxyBeanMethods = false)
public class GitHubStub {

    /** The e2e user's GitHub id: an id block of its own (the e2e backend has its own database anyway). */
    public static final long USER_ID = 9_000_901L;
    public static final String LOGIN = "octocat";

    private static final Set<String> GITHUB_HOSTS = Set.of("github.com", "api.github.com");

    @Bean
    RestClientCustomizer gitHubStubCustomizer() {
        return builder -> builder.requestInterceptor(new Interceptor());
    }

    static final class Interceptor implements ClientHttpRequestInterceptor {

        private final String accessToken = "e2e-" + UUID.randomUUID();

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            if (!GITHUB_HOSTS.contains(request.getURI().getHost())) {
                return execution.execute(request, body); // the LLM: its closed port fails fast
            }
            String path = request.getURI().getPath();
            HttpMethod method = request.getMethod();
            if (method == HttpMethod.POST && path.equals("/login/oauth/access_token")) {
                return json("{\"access_token\":\"" + accessToken + "\",\"scope\":\"read:user,user:email\",\"token_type\":\"bearer\"}");
            }
            if (method == HttpMethod.GET && path.equals("/user")) {
                return json("{\"id\":" + USER_ID + ",\"login\":\"" + LOGIN + "\"}");
            }
            if (method == HttpMethod.GET && path.equals("/user/emails")) {
                return json("[{\"email\":\"octocat@example.com\",\"primary\":true,\"verified\":true}]");
            }
            if (method == HttpMethod.POST && path.equals("/graphql")) {
                // Only the branch scan sends the user's node id (`userId`); user-insights sends `login`.
                String graphQl = new String(body, StandardCharsets.UTF_8);
                return json(resource(graphQl.contains("\"userId\"")
                        ? "github/branch-activity-response.json"
                        : "github/user-insights-response.json"));
            }
            return new MockClientHttpResponse(new byte[0], HttpStatus.NOT_FOUND);
        }

        private static ClientHttpResponse json(String body) {
            MockClientHttpResponse response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            return response;
        }

        private static String resource(String path) {
            try {
                return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
