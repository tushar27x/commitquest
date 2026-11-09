package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.github.model.GitHubUser;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionsCollection;
import com.tushar27x.insightshub.github.model.GitHubUser.PullRequest;
import com.tushar27x.insightshub.github.model.GitHubUser.Repository;
import com.tushar27x.insightshub.github.model.GitHubUser.RepositoryContributions;
import com.tushar27x.insightshub.github.model.GitHubUser.RepositoryName;
import com.tushar27x.insightshub.github.model.GitHubUser.TotalCount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.tushar27x.insightshub.insights.GitHubTestData.days;
import static com.tushar27x.insightshub.insights.GitHubTestData.repo;
import static com.tushar27x.insightshub.insights.GitHubTestData.user;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * B11 rule 5: GitHub's {@code total*Contributions} have no privacy filter, so with private access off the totals are the
 * sums of the per-repo lists without the private entries; with it on, GitHub's totals; private PRs leave the churn.
 */
class InsightsEngineTotalsTest {

    private static final ArchetypeScores FIXED = new ArchetypeScores(Archetype.PALADIN, Map.of(), 1);
    private final InsightsEngine engine = new InsightsEngine(habits -> FIXED);

    private static RepositoryContributions in(String name, boolean isPrivate, int count) {
        return new RepositoryContributions(new RepositoryName(name, isPrivate), new TotalCount(count));
    }

    /** 100 commits in all (90 private), 7 PRs (5 private), 4 issues (1 private). */
    private static GitHubUser mixed() {
        ContributionsCollection c = new ContributionsCollection(
                List.of(in("secret", true, 90), in("open", false, 10)),
                new GitHubUser.ContributionCalendar(5, List.of(new GitHubUser.Week(days("2026-10-01", 2)))),
                100, 7, 4, 0,
                List.of(in("secret", true, 5), in("open", false, 2)),
                List.of(in("secret", true, 1), in("open", false, 3)));
        Repository open = repo("open", 5, List.of("Java"), List.of("pom.xml"), List.of("init"));
        return user(List.of(open), List.of(
                new PullRequest(100, 10, "public pr", new RepositoryName(null, false)),
                new PullRequest(5000, 900, "private pr", new RepositoryName(null, true))), c);
    }

    @Test
    void withPrivateOffTheTotalsAreTheSumsOfThePublicEntries() {
        UserStats stats = engine.calculate(mixed(), false).stats();

        assertThat(stats.totalCommits()).isEqualTo(10);
        assertThat(stats.totalPrs()).isEqualTo(2);
        assertThat(stats.totalIssues()).isEqualTo(3);
    }

    @Test
    void withPrivateOnTheTotalsAreGitHubsOwn() {
        UserStats stats = engine.calculate(mixed(), true).stats();

        assertThat(stats.totalCommits()).isEqualTo(100);
        assertThat(stats.totalPrs()).isEqualTo(7);
        assertThat(stats.totalIssues()).isEqualTo(4);
    }

    @Test
    void theSingleArgumentCalculateTakesTheDataAsFetched() {
        assertThat(engine.calculate(mixed()).stats().totalCommits()).isEqualTo(100);
    }

    @Test
    void withPrivateOffAPrivatePullRequestLeavesTheChurn() {
        UserStats off = engine.calculate(mixed(), false).stats();
        UserStats on = engine.calculate(mixed(), true).stats();

        assertThat(off.codeChurn().additions()).isEqualTo(100);
        assertThat(off.codeChurn().deletions()).isEqualTo(10);
        assertThat(on.codeChurn().additions()).isEqualTo(5100);
        assertThat(on.codeChurn().deletions()).isEqualTo(910);
    }

    @Test
    void withPrivateOffAPrivatePullRequestLeavesTheHabitSignalsToo() {
        // the habit profile reads the pull requests: the average PR size must not include the private one
        HabitProfile off = engine.calculate(mixed(), false).habits();
        HabitProfile on = engine.calculate(mixed(), true).habits();

        assertThat(off.prCount()).isEqualTo(1);
        assertThat(on.prCount()).isEqualTo(2);
        assertThat(off.avgPrSize()).isLessThan(on.avgPrSize());
    }

    @Test
    void listsThatWereNotFetchedKeepGitHubsTotals() {
        ContributionsCollection c = new ContributionsCollection(List.of(in("open", false, 10)),
                new GitHubUser.ContributionCalendar(0, List.of()), 42, 7, 4, 0); // no per-repo PR / issue lists
        UserStats stats = engine.calculate(user(List.of(), List.of(), c), false).stats();

        assertThat(stats.totalCommits()).isEqualTo(10); // from the list that is there
        assertThat(stats.totalPrs()).isEqualTo(7);      // no list: the total stays
        assertThat(stats.totalIssues()).isEqualTo(4);
    }

    @Test
    void anEntryWithoutAnIsPrivateFlagCountsAsPublic() {
        ContributionsCollection c = new ContributionsCollection(
                List.of(new RepositoryContributions(new RepositoryName("x", null), new TotalCount(6))),
                new GitHubUser.ContributionCalendar(0, List.of()), 99, 0, 0, 0, List.of(), List.of());

        assertThat(engine.calculate(user(List.of(), List.of(), c), false).stats().totalCommits()).isEqualTo(6);
    }

    @Test
    void theRankedReposStayTheTopFiveThoughTheQueryNowListsUpToAHundred() {
        List<RepositoryContributions> many = java.util.stream.IntStream.rangeClosed(1, 30)
                .mapToObj(i -> in("repo-" + i, false, i)).toList();
        ContributionsCollection c = new ContributionsCollection(many,
                new GitHubUser.ContributionCalendar(0, List.of()), 465, 0, 0, 0, List.of(), List.of());

        UserStats stats = engine.calculate(user(List.of(), List.of(), c), true).stats();

        assertThat(stats.rankedRepos()).hasSize(5);
        assertThat(stats.rankedRepos().getFirst().name()).isEqualTo("repo-30");
    }
}
