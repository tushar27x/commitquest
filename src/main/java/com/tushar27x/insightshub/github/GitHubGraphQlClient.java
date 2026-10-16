package com.tushar27x.insightshub.github;

import com.tushar27x.insightshub.github.model.BranchActivityData;
import com.tushar27x.insightshub.github.model.GitHubUser.Connection;
import com.tushar27x.insightshub.github.model.GitHubUser.TotalCount;
import com.tushar27x.insightshub.github.model.GraphQlResponse;
import com.tushar27x.insightshub.github.model.UserInsightsData;
import com.tushar27x.insightshub.github.model.UserInsightsData.RateLimit;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * GitHub's GraphQL API: a user's profile, repositories and contribution history (port of v1's
 * {@code fetch_github_stats}), and the branch scan. The token is passed per call because every user
 * syncs with their own token.
 */
@Slf4j
@Component
public class GitHubGraphQlClient {

    /** GitHub rejects contribution ranges longer than one year. */
    static final Duration MAX_RANGE = Duration.ofDays(365);

    private static final ParameterizedTypeReference<GraphQlResponse<UserInsightsData>> INSIGHTS_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<GraphQlResponse<BranchActivityData>> BRANCH_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient gitHubRestClient;
    private final String userInsightsQuery;
    private final String branchActivityQuery;
    private final Clock clock;

    public GitHubGraphQlClient(RestClient gitHubRestClient,
                               @Value("classpath:graphql/user-insights.graphql") Resource userInsightsQuery,
                               @Value("classpath:graphql/branch-activity.graphql") Resource branchActivityQuery,
                               Clock clock) {
        this.gitHubRestClient = gitHubRestClient;
        this.userInsightsQuery = read(userInsightsQuery);
        this.branchActivityQuery = read(branchActivityQuery);
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

        GraphQlResponse<UserInsightsData> response = post(token, userInsightsQuery,
                Map.of("login", login, "from", from.toString(), "to", to.toString()),
                INSIGHTS_RESPONSE, "'" + login + "'");
        UserInsightsData data = unwrap(response, "'" + login + "'");
        if (data.user() == null) {
            throw new GitHubApiException("GitHub returned no user for '" + login + "'");
        }
        logRateLimit("for '" + login + "'", data.rateLimit());
        return data;
    }

    /**
     * Branch scan: the user's commits in {@code [since, until]} on the 25 newest branches of their 20
     * most recently pushed repos, summed (graphql/branch-activity.graphql). Repos last pushed before
     * {@code since} are skipped: no commit made inside the window has reached them yet.
     *
     * @param userNodeId the user's GraphQL node id ({@code User.id}), the {@code author} filter
     * @return the commit count; a commit on several branches counts once per branch
     */
    public int countBranchCommits(String token, String userNodeId, Instant since, Instant until) {
        if (!since.isBefore(until)) {
            throw new IllegalArgumentException("'since' must be before 'until'");
        }
        GraphQlResponse<BranchActivityData> response = post(token, branchActivityQuery,
                Map.of("userId", userNodeId, "since", since.toString(), "until", until.toString()),
                BRANCH_RESPONSE, "the branch scan");
        BranchActivityData data = unwrap(response, "the branch scan");
        if (data.viewer() == null) {
            throw new GitHubApiException("GitHub returned no viewer for the branch scan");
        }
        logRateLimit("for the branch scan", data.rateLimit());
        return countBranchCommits(data, since);
    }

    static int countBranchCommits(BranchActivityData data, Instant since) {
        return nodes(data.viewer().repositories()).stream()
                .filter(repo -> repo.pushedAt() != null && !repo.pushedAt().isBefore(since))
                .flatMap(repo -> nodes(repo.refs()).stream())
                .map(ref -> ref == null || ref.target() == null ? null : ref.target().history())
                .filter(Objects::nonNull)
                .mapToInt(TotalCount::totalCount)
                .sum();
    }

    private <T> GraphQlResponse<T> post(String token, String query, Map<String, Object> variables,
                                        ParameterizedTypeReference<GraphQlResponse<T>> type, String what) {
        try {
            return gitHubRestClient.post()
                    .uri("/graphql")
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query, "variables", variables))
                    .retrieve()
                    .onStatus(status -> status.isSameCodeAs(HttpStatus.UNAUTHORIZED), (request, res) -> {
                        throw new GitHubAuthException("GitHub rejected the token for " + what + " (HTTP 401)");
                    })
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        throw new GitHubApiException("GitHub GraphQL request failed for " + what
                                + ": HTTP " + res.getStatusCode().value());
                    })
                    .body(type);
        } catch (GitHubApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new GitHubApiException("Could not reach GitHub GraphQL API for " + what, e);
        }
    }

    private static <T> T unwrap(GraphQlResponse<T> response, String what) {
        if (response == null) {
            throw new GitHubApiException("Empty response from GitHub for " + what);
        }
        if (response.hasErrors()) {
            String messages = response.errors().stream()
                    .map(GraphQlResponse.Error::message)
                    .collect(Collectors.joining("; "));
            throw new GitHubApiException("GitHub GraphQL errors for " + what + ": " + messages);
        }
        if (response.data() == null) {
            throw new GitHubApiException("GitHub returned no data for " + what);
        }
        return response.data();
    }

    private static void logRateLimit(String what, RateLimit rateLimit) {
        if (rateLimit != null) {
            log.info("GitHub GraphQL {}: cost={} remaining={} resetAt={}",
                    what, rateLimit.cost(), rateLimit.remaining(), rateLimit.resetAt());
        }
    }

    private static <T> List<T> nodes(Connection<T> connection) {
        return connection == null || connection.nodes() == null ? List.of() : connection.nodes();
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource.getDescription(), e);
        }
    }
}
