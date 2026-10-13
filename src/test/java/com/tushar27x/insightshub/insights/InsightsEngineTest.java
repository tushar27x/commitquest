package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.github.model.GitHubUser;
import com.tushar27x.insightshub.github.model.GitHubUser.PullRequest;
import com.tushar27x.insightshub.insights.UserStats.RankedRepo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tushar27x.insightshub.insights.GitHubTestData.contributions;
import static com.tushar27x.insightshub.insights.GitHubTestData.day;
import static com.tushar27x.insightshub.insights.GitHubTestData.days;
import static com.tushar27x.insightshub.insights.GitHubTestData.emptyRepo;
import static com.tushar27x.insightshub.insights.GitHubTestData.repo;
import static com.tushar27x.insightshub.insights.GitHubTestData.repoContributions;
import static com.tushar27x.insightshub.insights.GitHubTestData.repoWithLanguages;
import static com.tushar27x.insightshub.insights.GitHubTestData.user;
import static org.assertj.core.api.Assertions.assertThat;

class InsightsEngineTest {

    /** Classifier stub: these tests are about stats; archetype rules have their own test. */
    private final InsightsEngine engine = new InsightsEngine((stats, days) -> Archetype.CODE_CRUSADER);

    private UserStats stats(GitHubUser user) {
        return engine.calculate(user).stats();
    }

    @Test
    void copiesTotalsAndBuildsSocialRatio() {
        UserStats stats = stats(user(contributions(40, 7, 3, List.of())));

        assertThat(stats.totalCommits()).isEqualTo(40);
        assertThat(stats.totalPrs()).isEqualTo(7);
        assertThat(stats.totalIssues()).isEqualTo(3);
        assertThat(stats.socialRatio()).isEqualTo("10/2");
    }

    @Test
    void ranksReposByContributionsDescendingKeepingOrderOnTies() {
        var contributions = contributions(0, 0, 0, List.of(), List.of(
                repoContributions("small", 2),
                repoContributions("big", 30),
                repoContributions("tie-first", 5),
                repoContributions("tie-second", 5)));

        assertThat(stats(user(contributions)).rankedRepos()).extracting(RankedRepo::name)
                .containsExactly("big", "tie-first", "tie-second", "small");
    }

    @Test
    void emptyRepositoryDoesNotCrash_B20() {
        // v1 crashed here: defaultBranchRef is null for a repo without commits
        GitHubUser user = user(
                List.of(repo("app", 3, List.of("Java"), List.of("pom.xml"), List.of("init")), emptyRepo("new-idea")),
                List.of(), contributions(1, 0, 0, List.of()));

        UserStats stats = stats(user);

        assertThat(stats.recentCommits()).containsExactly("init");
        assertThat(stats.repoDetails()).hasSize(2);
        assertThat(stats.repoDetails().get(1).readme()).isEmpty();
        assertThat(stats.repoDetails().get(1).files()).isEmpty();
    }

    @Test
    void collectsCommitMessagesAcrossReposSkippingBlankOnes() {
        GitHubUser user = user(List.of(
                        repo("a", 0, List.of(), List.of(), List.of("fix", "")),
                        repo("b", 0, List.of(), List.of(), List.of("feat: x"))),
                List.of(), null);

        assertThat(stats(user).recentCommits()).containsExactly("fix", "feat: x");
    }

    @Test
    void sumsCodeChurnAndStars() {
        GitHubUser user = user(
                List.of(repo("a", 5, List.of(), List.of(), List.of()), repo("b", 7, List.of(), List.of(), List.of())),
                List.of(new PullRequest(100, 10, "one"), new PullRequest(1, 40, "two")),
                null);

        UserStats stats = stats(user);

        assertThat(stats.codeChurn().additions()).isEqualTo(101);
        assertThat(stats.codeChurn().deletions()).isEqualTo(50);
        assertThat(stats.totalStars()).isEqualTo(12);
    }

    @Test
    void topLanguagesCountReposPerLanguageLimitedToFive() {
        GitHubUser user = user(List.of(
                        repoWithLanguages("r1", "Go", "Java"),
                        repoWithLanguages("r2", "Java", "Rust"),
                        repoWithLanguages("r3", "Java", "Python", "Go"),
                        repoWithLanguages("r4", "C", "Zig", "Elixir")),
                List.of(), null);

        // Java 3, Go 2, then ties at 1 in first-seen order: Rust, Python, C (Zig, Elixir cut)
        assertThat(stats(user).topLanguages()).containsExactly("Java", "Go", "Rust", "Python", "C");
    }

    @Test
    void countsActiveDays() {
        UserStats stats = stats(user(contributions(0, 0, 0,
                List.of(day("2026-10-01", 0), day("2026-10-02", 3), day("2026-10-03", 1)))));

        assertThat(stats.activeDaysPerYear()).isEqualTo(2);
    }

    @Test
    void weeklyHeatmapUsesLastSevenDaysInDateOrderEvenIfUnsorted() {
        // 9 days in reverse order: counts 9..1 for Oct 3..11 (2026-10-03 is a Saturday)
        List<GitHubUser.ContributionDay> reversed = days("2026-10-03", 9).reversed();

        UserStats stats = stats(user(contributions(0, 0, 0, reversed)));

        assertThat(stats.lastWeekHeatmap()).containsExactly(3, 4, 5, 6, 7, 8, 9);
        assertThat(stats.heatmapLabels()).containsExactly("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");
    }

    @Test
    void weeklyHeatmapIsPaddedAtTheFrontWhenFewerThanSevenDays() {
        UserStats stats = stats(user(contributions(0, 0, 0,
                List.of(day("2026-10-11", 1), day("2026-10-12", 2)))));

        assertThat(stats.lastWeekHeatmap()).containsExactly(0, 0, 0, 0, 0, 1, 2);
        assertThat(stats.heatmapLabels()).containsExactly("N/A", "N/A", "N/A", "N/A", "N/A", "Sun", "Mon");
    }

    @Test
    void emptyCalendarStillGivesSevenDayHeatmap() {
        // deliberate difference from v1, which returned [] here
        UserStats stats = stats(user(contributions(0, 0, 0, List.of())));

        assertThat(stats.lastWeekHeatmap()).containsExactly(0, 0, 0, 0, 0, 0, 0);
        assertThat(stats.heatmapLabels()).containsOnly("N/A").hasSize(7);
        assertThat(stats.lastMonthHeatmap()).isEmpty();
    }

    @Test
    void monthlyHeatmapUsesLastTwentyEightDays() {
        UserStats stats = stats(user(contributions(0, 0, 0, days("2026-09-01", 40))));

        assertThat(stats.lastMonthHeatmap()).hasSize(28).startsWith(13).endsWith(40);
    }

    @Test
    void handlesUserWithNoReposPullRequestsOrContributions() {
        GitHubUser bare = new GitHubUser("ghost", null, null, null, null, null, null, null, null, null);

        UserStats stats = stats(bare);

        assertThat(stats.totalCommits()).isZero();
        assertThat(stats.rankedRepos()).isEmpty();
        assertThat(stats.topLanguages()).isEmpty();
        assertThat(stats.socialRatio()).isEqualTo("0/0");
        assertThat(stats.lastWeekHeatmap()).hasSize(7);
    }

    @Test
    void passesStatsAndSortedDaysToTheClassifier() {
        var captured = new Object() {
            UserStats stats;
            List<GitHubUser.ContributionDay> days;
        };
        InsightsEngine capturing = new InsightsEngine((stats, days) -> {
            captured.stats = stats;
            captured.days = days;
            return Archetype.BUG_HUNTER;
        });

        InsightsResult result = capturing.calculate(user(contributions(1, 0, 0,
                List.of(day("2026-10-12", 2), day("2026-10-11", 1)))));

        assertThat(result.archetype()).isEqualTo(Archetype.BUG_HUNTER);
        assertThat(captured.stats).isSameAs(result.stats());
        assertThat(captured.days).extracting(GitHubUser.ContributionDay::contributionCount).containsExactly(1, 2);
    }
}
