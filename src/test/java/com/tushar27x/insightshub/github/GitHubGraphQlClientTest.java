package com.tushar27x.insightshub.github;

import com.tushar27x.insightshub.github.model.GitHubUser;
import com.tushar27x.insightshub.github.model.GitHubUser.Repository;
import com.tushar27x.insightshub.github.model.UserInsightsData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Unit tests against a mocked GitHub: no network, no Spring context. */
class GitHubGraphQlClientTest {

    private static final String GRAPHQL_URL = GitHubClientConfig.GITHUB_API_URL + "/graphql";
    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    private MockRestServiceServer github;
    private GitHubGraphQlClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        github = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = new GitHubClientConfig().gitHubRestClient(builder);
        client = new GitHubGraphQlClient(restClient,
                new ClassPathResource("graphql/user-insights.graphql"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void mapsGraphQlResponseIntoRecords() {
        github.expect(requestTo(GRAPHQL_URL))
                .andRespond(withSuccess(new ClassPathResource("github/user-insights-response.json"),
                        MediaType.APPLICATION_JSON));

        UserInsightsData data = client.fetchUserInsights("test-token", "octocat");

        assertThat(data.rateLimit().cost()).isEqualTo(1);
        assertThat(data.rateLimit().remaining()).isEqualTo(4999);

        GitHubUser user = data.user();
        assertThat(user.login()).isEqualTo("octocat");
        assertThat(user.bio()).isNull();
        assertThat(user.followers().totalCount()).isEqualTo(120);
        assertThat(user.repositories().nodes()).extracting(Repository::name)
                .containsExactly("hello-world", "empty-repo");

        Repository helloWorld = user.repositories().nodes().getFirst();
        assertThat(helloWorld.languages().nodes()).extracting(GitHubUser.Language::name)
                .containsExactly("Java", "Dockerfile");
        assertThat(helloWorld.readme().text()).isEqualTo("# Hello World");
        assertThat(helloWorld.tree().entries()).extracting(GitHubUser.TreeEntry::name)
                .containsExactly("Dockerfile", "src");

        var commits = helloWorld.defaultBranchRef().target().history().nodes();
        assertThat(commits).hasSize(2);
        assertThat(commits.getFirst().oid()).isEqualTo("a1b2c3d4e5f60718293a4b5c6d7e8f9012345678");
        assertThat(commits.getFirst().committedDate()).isEqualTo(Instant.parse("2026-10-12T18:30:00Z"));
        assertThat(commits.getFirst().author().user().login()).isEqualTo("octocat");
        assertThat(commits.get(1).author().user()).as("author without a GitHub account").isNull();

        assertThat(user.pullRequests().nodes()).hasSize(2);
        assertThat(user.contributionsCollection().totalCommitContributions()).isEqualTo(17);
        var days = user.contributionsCollection().contributionCalendar().weeks().getFirst().contributionDays();
        assertThat(days.getFirst().date()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(days.get(1).contributionCount()).isEqualTo(2);

        github.verify();
    }

    @Test
    void handlesMissingObjectsAsNull() {
        github.expect(requestTo(GRAPHQL_URL))
                .andRespond(withSuccess(new ClassPathResource("github/user-insights-response.json"),
                        MediaType.APPLICATION_JSON));

        Repository emptyRepo = client.fetchUserInsights("test-token", "octocat")
                .user().repositories().nodes().get(1);

        assertThat(emptyRepo.defaultBranchRef()).isNull();
        assertThat(emptyRepo.readme()).isNull();
        assertThat(emptyRepo.tree()).isNull();
        assertThat(emptyRepo.languages().nodes()).isEmpty();
    }

    @Test
    void sendsTokenQueryAndOneYearWindowByDefault() {
        github.expect(requestTo(GRAPHQL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(header(HttpHeaders.USER_AGENT, "insightshub"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query", containsString("contributionsCollection(from: $from, to: $to)")))
                .andExpect(jsonPath("$.variables.login").value("octocat"))
                .andExpect(jsonPath("$.variables.from").value("2025-10-13T10:00:00Z"))
                .andExpect(jsonPath("$.variables.to").value("2026-10-13T10:00:00Z"))
                .andRespond(withSuccess(new ClassPathResource("github/user-insights-response.json"),
                        MediaType.APPLICATION_JSON));

        client.fetchUserInsights("test-token", "octocat");

        github.verify();
    }

    @Test
    void sendsCustomWindow() {
        Instant from = Instant.parse("2026-10-06T00:00:00Z");
        Instant to = Instant.parse("2026-10-13T00:00:00Z");
        github.expect(requestTo(GRAPHQL_URL))
                .andExpect(jsonPath("$.variables.from").value("2026-10-06T00:00:00Z"))
                .andExpect(jsonPath("$.variables.to").value("2026-10-13T00:00:00Z"))
                .andRespond(withSuccess(new ClassPathResource("github/user-insights-response.json"),
                        MediaType.APPLICATION_JSON));

        client.fetchUserInsights("test-token", "octocat", from, to);

        github.verify();
    }

    @Test
    void graphQlErrorsBecomeApiException() {
        github.expect(requestTo(GRAPHQL_URL)).andRespond(withSuccess("""
                {
                  "data": { "rateLimit": null, "user": null },
                  "errors": [
                    {
                      "type": "NOT_FOUND",
                      "path": ["user"],
                      "locations": [ { "line": 7, "column": 3 } ],
                      "message": "Could not resolve to a User with the login of 'ghost-404'."
                    }
                  ]
                }
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchUserInsights("test-token", "ghost-404"))
                .isExactlyInstanceOf(GitHubApiException.class)
                .hasMessageContaining("Could not resolve to a User with the login of 'ghost-404'");
    }

    @Test
    void nullUserWithoutErrorsBecomesApiException() {
        github.expect(requestTo(GRAPHQL_URL)).andRespond(withSuccess("""
                { "data": { "rateLimit": { "cost": 1, "remaining": 4998, "resetAt": "2026-10-13T11:00:00Z" }, "user": null } }
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchUserInsights("test-token", "octocat"))
                .isExactlyInstanceOf(GitHubApiException.class)
                .hasMessageContaining("no user");
    }

    @Test
    void unauthorizedBecomesAuthException() {
        github.expect(requestTo(GRAPHQL_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Bad credentials\"}"));

        assertThatThrownBy(() -> client.fetchUserInsights("revoked-token", "octocat"))
                .isInstanceOf(GitHubAuthException.class)
                .hasMessageContaining("401")
                .hasMessageNotContaining("revoked-token");
    }

    @Test
    void serverErrorBecomesApiException() {
        github.expect(requestTo(GRAPHQL_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchUserInsights("test-token", "octocat"))
                .isExactlyInstanceOf(GitHubApiException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void rejectsInvalidWindowsWithoutCallingGitHub() {
        github.expect(never(), requestTo(GRAPHQL_URL));

        assertThatThrownBy(() -> client.fetchUserInsights("t", "octocat", NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.fetchUserInsights("t", "octocat",
                NOW.minus(GitHubGraphQlClient.MAX_RANGE).minusSeconds(1), NOW))
                .isInstanceOf(IllegalArgumentException.class);

        github.verify();
    }
}
