package com.tushar27x.insightshub.github;

import com.tushar27x.insightshub.github.model.GraphQlResponse;
import com.tushar27x.insightshub.github.model.UserInsightsData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fetches a user's profile, repositories and contribution history from GitHub's GraphQL API.
 * Port of v1's {@code fetch_github_stats}. The token is passed per call because every user
 * syncs with their own token.
 */
@Slf4j
@Component
public class GitHubGraphQlClient {

    /** GitHub rejects contribution ranges longer than one year. */
    static final Duration MAX_RANGE = Duration.ofDays(365);

    private static final ParameterizedTypeReference<GraphQlResponse<UserInsightsData>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient gitHubRestClient;
    private final String userInsightsQuery;
    private final Clock clock;

    public GitHubGraphQlClient(RestClient gitHubRestClient,
                               @Value("classpath:graphql/user-insights.graphql") Resource userInsightsQuery,
                               Clock clock) {
        this.gitHubRestClient = gitHubRestClient;
        this.userInsightsQuery = read(userInsightsQuery);
        this.clock = clock;
    }

    /** Profile, repositories and the last year of contributions. */
    public UserInsightsData fetchUserInsights(String token, String login) {
        Instant to = clock.instant();
        return fetchUserInsights(token, login, to.minus(MAX_RANGE), to);
    }

    /** Same, with contributions limited to {@code [from, to]} (at most one year). */
    public UserInsightsData fetchUserInsights(String token, String login, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("'from' must be before 'to'");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("GitHub allows at most one year between 'from' and 'to'");
        }

        Map<String, Object> body = Map.of(
                "query", userInsightsQuery,
                "variables", Map.of("login", login, "from", from.toString(), "to", to.toString()));

        GraphQlResponse<UserInsightsData> response;
        try {
            response = gitHubRestClient.post()
                    .uri("/graphql")
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.isSameCodeAs(HttpStatus.UNAUTHORIZED), (request, res) -> {
                        throw new GitHubAuthException("GitHub rejected the token for '" + login + "' (HTTP 401)");
                    })
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        throw new GitHubApiException("GitHub GraphQL request failed for '" + login
                                + "': HTTP " + res.getStatusCode().value());
                    })
                    .body(RESPONSE_TYPE);
        } catch (GitHubApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new GitHubApiException("Could not reach GitHub GraphQL API for '" + login + "'", e);
        }

        return unwrap(response, login);
    }

    private UserInsightsData unwrap(GraphQlResponse<UserInsightsData> response, String login) {
        if (response == null) {
            throw new GitHubApiException("Empty response from GitHub for '" + login + "'");
        }
        if (response.hasErrors()) {
            String messages = response.errors().stream()
                    .map(GraphQlResponse.Error::message)
                    .collect(Collectors.joining("; "));
            throw new GitHubApiException("GitHub GraphQL errors for '" + login + "': " + messages);
        }
        UserInsightsData data = response.data();
        if (data == null || data.user() == null) {
            throw new GitHubApiException("GitHub returned no user for '" + login + "'");
        }
        if (data.rateLimit() != null) {
            log.info("GitHub GraphQL for '{}': cost={} remaining={} resetAt={}",
                    login, data.rateLimit().cost(), data.rateLimit().remaining(), data.rateLimit().resetAt());
        }
        return data;
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource.getDescription(), e);
        }
    }
}
