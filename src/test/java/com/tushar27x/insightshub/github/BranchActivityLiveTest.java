package com.tushar27x.insightshub.github;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B3's open questions, against the real GitHub API. Skipped unless GITHUB_TOKEN is set:
 * <pre>GITHUB_TOKEN=... ./mvnw test -Dtest=BranchActivityLiveTest</pre>
 * Prints its findings, so the run documents what GitHub did that day.
 */
@EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".+")
class BranchActivityLiveTest {

    /** A busy public repo whose maintainers rebase and cherry-pick, so author and committer dates differ. */
    private static final String OWNER = "spring-projects";
    private static final String REPO = "spring-boot";
    private static final Duration WINDOW = Duration.ofHours(1);

    private final String token = System.getenv("GITHUB_TOKEN");
    private final RestClient rest = new GitHubClientConfig().gitHubRestClient(RestClient.builder());
    private final GitHubGraphQlClient client = new GitHubGraphQlClient(rest,
            new ClassPathResource("graphql/user-insights.graphql"),
            new ClassPathResource("graphql/branch-activity.graphql"),
            Clock.systemUTC());

    @Test
    void branchScanRunsForTheTokensOwner() {
        String nodeId = query("query { viewer { id } }", Map.of()).at("/data/viewer/id").asString();
        Instant now = Instant.now();

        int commits = client.countBranchCommits(token, nodeId, now.minus(Duration.ofDays(7)), now);

        System.out.println("Branch scan, last 7 days: " + commits + " commits");
        assertThat(commits).isNotNegative();
    }

    @Test
    void historySinceUntilFiltersOnOneOfTheTwoDates() {
        JsonNode commits = query("""
                query($owner: String!, $name: String!) {
                  repository(owner: $owner, name: $name) {
                    defaultBranchRef { target { ... on Commit {
                      history(first: 100) { nodes { oid authoredDate committedDate } }
                    } } }
                  }
                }""", Map.of("owner", OWNER, "name", REPO))
                .at("/data/repository/defaultBranchRef/target/history/nodes");

        // A commit committed at least two days after it was authored: the two windows can't overlap
        JsonNode commit = StreamSupport.stream(commits.spliterator(), false)
                .filter(node -> Duration.between(Instant.parse(node.get("authoredDate").asString()),
                        Instant.parse(node.get("committedDate").asString())).toDays() >= 2)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No commit with author and committer dates 2+ days apart"));
        String oid = commit.get("oid").asString();
        Instant authored = Instant.parse(commit.get("authoredDate").asString());
        Instant committed = Instant.parse(commit.get("committedDate").asString());

        boolean foundByAuthorDate = inWindow(oid, authored);
        boolean foundByCommitterDate = inWindow(oid, committed);

        System.out.printf("history(since, until) on %s/%s %s: authored %s → %s, committed %s → %s%n",
                OWNER, REPO, oid, authored, foundByAuthorDate ? "found" : "not found",
                committed, foundByCommitterDate ? "found" : "not found");
        assertThat(foundByAuthorDate).as("exactly one of the dates is filtered on").isNotEqualTo(foundByCommitterDate);
    }

    private boolean inWindow(String oid, Instant at) {
        JsonNode nodes = query("""
                query($owner: String!, $name: String!, $since: GitTimestamp!, $until: GitTimestamp!) {
                  repository(owner: $owner, name: $name) {
                    defaultBranchRef { target { ... on Commit {
                      history(first: 100, since: $since, until: $until) { nodes { oid } }
                    } } }
                  }
                }""", Map.of("owner", OWNER, "name", REPO,
                "since", at.minus(WINDOW).toString(), "until", at.plus(WINDOW).toString()))
                .at("/data/repository/defaultBranchRef/target/history/nodes");
        return StreamSupport.stream(nodes.spliterator(), false).anyMatch(node -> oid.equals(node.get("oid").asString()));
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
