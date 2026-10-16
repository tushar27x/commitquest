package com.tushar27x.insightshub.github;

import com.tushar27x.insightshub.github.model.UserInsightsData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Calls the real GitHub API. Skipped unless GITHUB_TOKEN is set, so normal builds stay offline:
 * <pre>GITHUB_TOKEN=... [GITHUB_LOGIN=...] ./mvnw test -Dtest=GitHubGraphQlClientLiveTest</pre>
 */
@EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
class GitHubGraphQlClientLiveTest {

    private final String token = System.getenv("GITHUB_TOKEN");
    private final String login = System.getenv().getOrDefault("GITHUB_LOGIN", "tushar27x");

    private final GitHubGraphQlClient client = new GitHubGraphQlClient(
            new GitHubClientConfig().gitHubRestClient(RestClient.builder()),
            new ClassPathResource("graphql/user-insights.graphql"),
            new ClassPathResource("graphql/branch-activity.graphql"),
            Clock.systemUTC());

    @Test
    void fetchesRealUser() {
        UserInsightsData data = client.fetchUserInsights(token, login);

        assertThat(data.user().login()).isEqualToIgnoringCase(login);
        assertThat(data.rateLimit().remaining()).isPositive();
        assertThat(data.user().contributionsCollection().contributionCalendar().weeks())
                .as("a one-year calendar has ~53 weeks")
                .hasSizeBetween(52, 54);
    }

    @Test
    void invalidTokenIsRejected() {
        assertThatThrownBy(() -> client.fetchUserInsights("ghp_definitely-not-a-valid-token", login))
                .isInstanceOf(GitHubAuthException.class);
    }
}
