package com.tushar27x.insightshub.github;

import com.tushar27x.insightshub.github.model.UserInsightsData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** B10: the GraphQL client asks for PUBLIC repos unless told otherwise, and reads the token's scopes. */
class GitHubGraphQlClientPrivateTest {

    private static final String GRAPHQL_URL = GitHubClientConfig.GITHUB_API_URL + "/graphql";
    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    private static final Instant FROM = NOW.minusSeconds(86_400);

    private MockRestServiceServer github;
    private GitHubGraphQlClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        github = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubGraphQlClient(new GitHubClientConfig().gitHubRestClient(builder),
                new ClassPathResource("graphql/user-insights.graphql"),
                new ClassPathResource("graphql/branch-activity.graphql"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void bothQueriesTakeThePrivacyVariable() {
        github.expect(requestTo(GRAPHQL_URL))
                .andExpect(jsonPath("$.query", containsString("repositories(first: 5, privacy: $privacy")))
                .andExpect(jsonPath("$.query", containsString("isPrivate")))
                .andRespond(withSuccess(new ClassPathResource("github/user-insights-response.json"),
                        MediaType.APPLICATION_JSON));
        github.expect(requestTo(GRAPHQL_URL))
                .andExpect(jsonPath("$.query", containsString("privacy: $privacy")))
                .andRespond(withSuccess(new ClassPathResource("github/branch-activity-response.json"),
                        MediaType.APPLICATION_JSON));

        client.fetchUserInsights("test-token", "octocat", FROM, NOW, false);
        client.countBranchCommits("test-token", "U_octocat", FROM, NOW, false);
        github.verify();
    }

    @Test
    void publicOnlyIsTheDefaultForEveryOverload() {
        github.expect(requestTo(GRAPHQL_URL)).andExpect(jsonPath("$.variables.privacy").value("PUBLIC"))
                .andRespond(withSuccess(new ClassPathResource("github/user-insights-response.json"),
                        MediaType.APPLICATION_JSON));
        github.expect(requestTo(GRAPHQL_URL)).andExpect(jsonPath("$.variables.privacy").value("PUBLIC"))
                .andRespond(withSuccess(new ClassPathResource("github/user-insights-response.json"),
                        MediaType.APPLICATION_JSON));
        github.expect(requestTo(GRAPHQL_URL)).andExpect(jsonPath("$.variables.privacy").value("PUBLIC"))
                .andRespond(withSuccess(new ClassPathResource("github/branch-activity-response.json"),
                        MediaType.APPLICATION_JSON));

        client.fetchUserInsights("test-token", "octocat");
        client.fetchUserInsights("test-token", "octocat", FROM, NOW);
        client.countBranchCommits("test-token", "U_octocat", FROM, NOW);
        github.verify();
    }

    @Test
    void includingPrivateLiftsTheFilterForBothQueries() {
        github.expect(requestTo(GRAPHQL_URL)).andExpect(jsonPath("$.variables.privacy").doesNotExist())
                .andRespond(withSuccess(new ClassPathResource("github/user-insights-response.json"),
                        MediaType.APPLICATION_JSON));
        github.expect(requestTo(GRAPHQL_URL)).andExpect(jsonPath("$.variables.privacy").doesNotExist())
                .andRespond(withSuccess(new ClassPathResource("github/branch-activity-response.json"),
                        MediaType.APPLICATION_JSON));

        client.fetchUserInsights("test-token", "octocat", FROM, NOW, true);
        client.countBranchCommits("test-token", "U_octocat", FROM, NOW, true);
        github.verify();
    }

    @Test
    void theResponsesScopesHeaderIsReturned() {
        github.expect(requestTo(GRAPHQL_URL))
                .andRespond(withSuccess(new ClassPathResource("github/user-insights-response.json"),
                        MediaType.APPLICATION_JSON).header("X-OAuth-Scopes", "read:user, repo, user:email"));

        UserInsightsData data = client.fetchUserInsights("test-token", "octocat", FROM, NOW, true);

        assertThat(data.oauthScopes()).isEqualTo("read:user, repo, user:email");
    }

    @Test
    void noScopesHeaderMeansNull() {
        github.expect(requestTo(GRAPHQL_URL))
                .andRespond(withSuccess(new ClassPathResource("github/user-insights-response.json"),
                        MediaType.APPLICATION_JSON));

        assertThat(client.fetchUserInsights("test-token", "octocat", FROM, NOW, false).oauthScopes()).isNull();
    }

    @Test
    void isPrivateIsMappedAndAbsentMeansPublic() {
        github.expect(requestTo(GRAPHQL_URL)).andRespond(withSuccess("""
                {"data": {"user": {"id": "U_1", "login": "octocat", "repositories": {"nodes": [
                  {"name": "a", "isPrivate": true, "stargazerCount": 1},
                  {"name": "b", "isPrivate": false, "stargazerCount": 1},
                  {"name": "c", "isPrivate": null, "stargazerCount": 1},
                  {"name": "d", "stargazerCount": 1}]},
                  "contributionsCollection": {"commitContributionsByRepository": [
                    {"repository": {"name": "a", "isPrivate": true}, "contributions": {"totalCount": 3}}],
                    "totalCommitContributions": 3, "totalPullRequestContributions": 0,
                    "totalIssueContributions": 0, "totalRepositoryContributions": 0}}}}
                """, MediaType.APPLICATION_JSON));

        var user = client.fetchUserInsights("test-token", "octocat", FROM, NOW, true).user();

        assertThat(user.repositories().nodes()).extracting(r -> r.privateRepo())
                .containsExactly(true, false, false, false);
        assertThat(user.contributionsCollection().commitContributionsByRepository().getFirst().repository().privateRepo())
                .isTrue();
    }

    @Test
    void aForbiddenErrorIsAScopeProblemNotAGenericFailure() {
        github.expect(requestTo(GRAPHQL_URL)).andRespond(withSuccess("""
                {"data": null, "errors": [{"type": "FORBIDDEN", "message": "Resource not accessible"}]}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchUserInsights("test-token", "octocat", FROM, NOW, true))
                .isInstanceOf(GitHubScopeException.class)
                .isInstanceOf(GitHubApiException.class)
                .hasMessageNotContaining("test-token");
    }

    @Test
    void otherGraphQlErrorsStayGeneric() {
        github.expect(requestTo(GRAPHQL_URL)).andRespond(withSuccess("""
                {"data": null, "errors": [{"type": "NOT_FOUND", "message": "no such user"}]}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchUserInsights("test-token", "octocat", FROM, NOW, true))
                .isInstanceOf(GitHubApiException.class).isNotInstanceOf(GitHubScopeException.class);
        assertThat(HttpHeaders.AUTHORIZATION).isNotBlank();
    }
}
