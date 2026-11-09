package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.github.model.GitHubUser;
import com.tushar27x.insightshub.github.model.GitHubUser.Repository;
import com.tushar27x.insightshub.github.model.GitHubUser.RepositoryContributions;
import com.tushar27x.insightshub.github.model.GitHubUser.RepositoryName;
import com.tushar27x.insightshub.github.model.GitHubUser.TotalCount;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static com.tushar27x.insightshub.insights.GitHubTestData.contributions;
import static com.tushar27x.insightshub.insights.GitHubTestData.repo;
import static com.tushar27x.insightshub.insights.GitHubTestData.user;
import static org.assertj.core.api.Assertions.assertThat;

/** B10 (Q2): private repos count in the aggregates and are never named, described or quoted. */
class InsightsEnginePrivateTest {

    private static final ArchetypeScores FIXED = new ArchetypeScores(Archetype.PALADIN, Map.of(), 1);
    private final InsightsEngine engine = new InsightsEngine(habits -> FIXED);

    private static Repository asPrivate(Repository r) {
        return new Repository(r.name(), r.stargazerCount(), r.languages(), r.defaultBranchRef(), r.readme(), r.tree(), true);
    }

    private GitHubUser withPrivateRepo() {
        Repository open = repo("open-source", 5, List.of("Java"), List.of("pom.xml"), List.of("feat: public work"));
        Repository secret = asPrivate(repo("secret-project", 30, List.of("Rust"), List.of("secret.key"),
                List.of("feat: the secret launch plan")));
        var byRepo = List.of(
                new RepositoryContributions(new RepositoryName("secret-project", true), new TotalCount(90)),
                new RepositoryContributions(new RepositoryName("open-source", false), new TotalCount(10)));
        return user(List.of(open, secret), List.of(), contributions(100, 0, 0, List.of(), byRepo));
    }

    @Test
    void aPrivateRepoIsNeverListedByName() {
        UserStats stats = engine.calculate(withPrivateRepo()).stats();

        assertThat(stats.rankedRepos()).extracting(UserStats.RankedRepo::name).containsExactly("open-source");
        assertThat(stats.repoDetails()).extracting(UserStats.RepoDetail::name).containsExactly("open-source");
        assertThat(stats.recentCommits()).containsExactly("feat: public work");
    }

    @Test
    void nothingPrivateSurvivesInTheSerializedStats() {
        String json = JsonMapper.builder().build().writeValueAsString(engine.calculate(withPrivateRepo()).stats());

        assertThat(json).doesNotContain("secret-project").doesNotContain("secret.key").doesNotContain("launch plan");
    }

    @Test
    void aPrivateRepoStillCountsInTheTotalsAndAggregates() {
        UserStats stats = engine.calculate(withPrivateRepo()).stats();

        assertThat(stats.totalCommits()).isEqualTo(100); // GitHub's own total, private work included
        assertThat(stats.totalStars()).isEqualTo(35);
        assertThat(stats.topLanguages()).containsExactly("Java", "Rust");
    }

    @Test
    void withoutPrivateReposTheOutputIsUnchanged() {
        Repository open = repo("open-source", 5, List.of("Java"), List.of("pom.xml"), List.of("init"));
        UserStats stats = engine.calculate(user(List.of(open), List.of(), contributions(1, 0, 0, List.of(), List.of(
                new RepositoryContributions(new RepositoryName("open-source"), new TotalCount(1)))))).stats();

        assertThat(stats.rankedRepos()).hasSize(1);
        assertThat(stats.repoDetails()).hasSize(1);
    }
}
