package com.tushar27x.insightshub.github;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B11's one-off probe of "Totals when OFF" (contract.md), against the real GitHub API. Skipped unless GITHUB_TOKEN is
 * set, and meant for a token with the {@code repo} scope of a user who has private repos:
 * <pre>GITHUB_TOKEN=... ./mvnw test -Dtest=PrivateTotalsLiveTest</pre>
 * For one window it prints what each figure counts, so the run documents whether the table holds:
 * <ul>
 *   <li>{@code totalCommitContributions}: includes private work, no filter;</li>
 *   <li>the sum of {@code commitContributionsByRepository} with and without the {@code isPrivate} entries (what the
 *       app now stores when private access is off);</li>
 *   <li>{@code restrictedContributionsCount} and the calendar total;</li>
 *   <li>the same for pull requests and issues, and {@code pullRequests(first:10)} nodes with their repository's
 *       {@code isPrivate}.</li>
 * </ul>
 * It never prints a repository name, only counts. The only hard assertions are the ones the app relies on: the
 * per-repo entries carry a boolean {@code isPrivate}, and the public sum never exceeds the all-repos sum.
 */
@EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
class PrivateTotalsLiveTest {

    private static final String QUERY = """
            query($login: String!, $from: DateTime!, $to: DateTime!) {
              user(login: $login) {
                pullRequests(first: 10, orderBy: {field: CREATED_AT, direction: DESC}) {
                  nodes { additions deletions repository { isPrivate } }
                }
                contributionsCollection(from: $from, to: $to) {
                  totalCommitContributions
                  totalPullRequestContributions
                  totalIssueContributions
                  restrictedContributionsCount
                  contributionCalendar { totalContributions }
                  commitContributionsByRepository(maxRepositories: 100) {
                    repository { isPrivate } contributions { totalCount }
                  }
                  pullRequestContributionsByRepository(maxRepositories: 100) {
                    repository { isPrivate } contributions { totalCount }
                  }
                  issueContributionsByRepository(maxRepositories: 100) {
                    repository { isPrivate } contributions { totalCount }
                  }
                }
              }
            }""";

    private final String token = System.getenv("GITHUB_TOKEN");
    private final RestClient rest = new GitHubClientConfig().gitHubRestClient(RestClient.builder());

    @Test
    void whatEachTotalCountsWithAndWithoutPrivateEntries() {
        String login = query("query { viewer { login } }", Map.of()).at("/data/viewer/login").asString();
        Instant to = Instant.now();
        JsonNode user = query(QUERY, Map.of("login", login, "from", to.minus(Duration.ofDays(364)).toString(),
                "to", to.toString())).at("/data/user");
        JsonNode c = user.get("contributionsCollection");

        Sums commits = sums(c.get("commitContributionsByRepository"));
        Sums prs = sums(c.get("pullRequestContributionsByRepository"));
        Sums issues = sums(c.get("issueContributionsByRepository"));

        System.out.printf("Last 364 days for the token's owner (private access %s):%n",
                commits.privateCount > 0 || prs.privateCount > 0 ? "visible" : "NOT visible: nothing to compare");
        System.out.printf("  commits: totalCommitContributions=%d, per-repo sum all=%d (public=%d, private=%d, %d repos)%n",
                c.get("totalCommitContributions").asInt(), commits.all, commits.publicCount, commits.privateCount, commits.repos);
        System.out.printf("  pull requests: total=%d, per-repo sum all=%d (public=%d, private=%d)%n",
                c.get("totalPullRequestContributions").asInt(), prs.all, prs.publicCount, prs.privateCount);
        System.out.printf("  issues: total=%d, per-repo sum all=%d (public=%d, private=%d)%n",
                c.get("totalIssueContributions").asInt(), issues.all, issues.publicCount, issues.privateCount);
        System.out.printf("  restrictedContributionsCount=%d, calendar total=%d%n",
                c.get("restrictedContributionsCount").asInt(), c.at("/contributionCalendar/totalContributions").asInt());
        long privatePrNodes = StreamSupport.stream(user.get("pullRequests").get("nodes").spliterator(), false)
                .filter(node -> node.at("/repository/isPrivate").asBoolean()).count();
        System.out.printf("  pullRequests(first:10): %d nodes, %d in private repos%n",
                user.get("pullRequests").get("nodes").size(), privatePrNodes);
        System.out.println("  Expected by the contract: total*Contributions include private work, the public sums "
                + "do not, restricted/calendar have no per-repo breakdown.");

        assertThat(commits.publicCount).isLessThanOrEqualTo(commits.all);
        assertThat(prs.publicCount).isLessThanOrEqualTo(prs.all);
        assertThat(issues.publicCount).isLessThanOrEqualTo(issues.all);
    }

    private static Sums sums(JsonNode entries) {
        Sums sums = new Sums();
        for (JsonNode entry : entries) {
            JsonNode isPrivate = entry.at("/repository/isPrivate");
            assertThat(isPrivate.isBoolean()).as("every per-repo entry says whether its repo is private").isTrue();
            int count = entry.at("/contributions/totalCount").asInt();
            sums.repos++;
            sums.all += count;
            if (isPrivate.asBoolean()) {
                sums.privateCount += count;
            } else {
                sums.publicCount += count;
            }
        }
        return sums;
    }

    private static final class Sums {
        int repos;
        int all;
        int publicCount;
        int privateCount;
    }

    private JsonNode query(String query, Map<String, Object> variables) {
        JsonNode response = rest.post()
                .uri("/graphql")
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", query, "variables", variables))
                .retrieve()
                .body(JsonNode.class);
        assertThat(response).isNotNull();
        assertThat(response.has("errors")).as("GraphQL errors: %s", response.get("errors")).isFalse();
        return response;
    }
}
