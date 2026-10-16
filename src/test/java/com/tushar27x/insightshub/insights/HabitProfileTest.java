package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.github.model.GitHubUser;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import com.tushar27x.insightshub.github.model.GitHubUser.PullRequest;
import com.tushar27x.insightshub.github.model.GitHubUser.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.tushar27x.insightshub.insights.GitHubTestData.commit;
import static com.tushar27x.insightshub.insights.GitHubTestData.contributions;
import static com.tushar27x.insightshub.insights.GitHubTestData.day;
import static com.tushar27x.insightshub.insights.GitHubTestData.emptyRepo;
import static com.tushar27x.insightshub.insights.GitHubTestData.repo;
import static com.tushar27x.insightshub.insights.GitHubTestData.repoWithLanguages;
import static com.tushar27x.insightshub.insights.GitHubTestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HabitProfileTest {

    private static HabitProfile profile(GitHubUser user) {
        return HabitProfile.from(user, InsightsEngine.sortedDays(user.contributionsCollection()));
    }

    private static HabitProfile profile(List<ContributionDay> days) {
        return profile(user(contributions(0, 0, 0, days)));
    }

    @Test
    void userWithNothingHasAllZeroSignals() {
        GitHubUser ghost = new GitHubUser(null, "ghost", null, null, null, null, null, null, null, null, null);

        assertThat(profile(ghost)).isEqualTo(new HabitProfile(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void issueAndPrSharesComeFromContributionTotals() {
        HabitProfile habits = profile(user(contributions(60, 20, 20, List.of())));

        assertThat(habits.issueShare()).isEqualTo(0.2);   // 20 / (60 + 20 + 20)
        assertThat(habits.prShare()).isEqualTo(0.25);     // 20 / (60 + 20)
    }

    @Test
    void activeDayRatioAndLongestStreak() {
        HabitProfile habits = profile(List.of(
                day("2026-10-01", 1), day("2026-10-02", 2), day("2026-10-03", 0),
                day("2026-10-04", 1), day("2026-10-05", 1), day("2026-10-06", 1), day("2026-10-07", 0)));

        assertThat(habits.activeDayRatio()).isEqualTo(5 / 7.0);
        assertThat(habits.longestStreak()).isEqualTo(3);
    }

    @Test
    void streakBreaksOnAMissingCalendarDay() {
        // no entry for Oct 3: the days are not consecutive
        assertThat(profile(List.of(day("2026-10-01", 1), day("2026-10-02", 1), day("2026-10-04", 1)))
                .longestStreak()).isEqualTo(2);
    }

    @Test
    void weekendShareOfAllContributions() {
        // 2026-10-10 is a Saturday
        HabitProfile habits = profile(List.of(day("2026-10-10", 3), day("2026-10-11", 1), day("2026-10-12", 4)));

        assertThat(habits.weekendShare()).isEqualTo(0.5);
    }

    @Test
    void burstinessIsBusiestDayOverAverageActiveDay() {
        // active counts 1, 1, 1, 9: mean 3, max 9; the zero day is ignored
        HabitProfile habits = profile(List.of(day("2026-10-01", 1), day("2026-10-02", 1), day("2026-10-03", 0),
                day("2026-10-04", 1), day("2026-10-05", 9)));

        assertThat(habits.burstiness()).isEqualTo(3.0);
    }

    @Test
    void commitSignalsUseOnlyTheUsersOwnCommitSubjects() {
        Repository repo = repo("app", "", List.of(), List.of(
                commit("fix: null pointer in parser", "octocat"),
                commit("wip", "OctoCat"),                                   // login match ignores case
                commit("Add caching layer to client\n\nlong body fixes stuff", "octocat"), // subject line only
                commit("fix everything quickly", "someone-else"),           // someone else's commit
                commit("Fixed the flaky test suite", null)));               // email not linked to an account

        HabitProfile habits = profile(user(List.of(repo), List.of(), null));

        assertThat(habits.ownCommitCount()).isEqualTo(3);
        assertThat(habits.fixCommitShare()).isCloseTo(1 / 3.0, within(1e-9));
        assertThat(habits.descriptiveCommitShare()).isCloseTo(2 / 3.0, within(1e-9));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Fix typo", "bugfix: login", "hotfix", "Resolves #12", "fixed tests", "patch release"})
    void fixSubjects(String subject) {
        assertThat(HabitProfile.isFix(subject)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"prefix handling", "add test fixture", "debugging session", "Add login page"})
    void notFixSubjects(String subject) {
        assertThat(HabitProfile.isFix(subject)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Dockerfile", "docker-compose.yml", "main.tf", "Dockerfile.dev", "k8s", "Jenkinsfile",
            "terraform"})
    void infraEntries(String file) {
        assertThat(HabitProfile.hasInfraFiles(repo("r", "", List.of(file), List.of()))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"application.yml", "config", "README.md", ".github", "Makefile", "pom.xml"})
    void notInfraEntries(String file) {
        // v1 matched "yml" / "config" substrings, which made almost everyone an architect
        assertThat(HabitProfile.hasInfraFiles(repo("r", "", List.of(file), List.of()))).isFalse();
    }

    @Test
    void repoSharesLanguagesAndAudience() {
        GitHubUser user = user(List.of(
                        repo("infra", "short", List.of("Dockerfile"), List.of()),
                        repo("docs", "x".repeat(HabitProfile.DOCUMENTED_README_CHARS), List.of("src"), List.of()),
                        repoWithLanguages("polyglot", "Java", "Go", "Rust"),
                        emptyRepo("new-idea")),
                List.of(), null);

        HabitProfile habits = profile(user);

        assertThat(habits.infraRepoShare()).isEqualTo(0.25);
        assertThat(habits.documentedRepoShare()).isEqualTo(0.25);
        assertThat(habits.languageCount()).isEqualTo(3);
        assertThat(habits.audience()).isEqualTo(10);  // 10 followers, 0 stars
    }

    @Test
    void averagePullRequestSize() {
        HabitProfile habits = profile(user(List.of(),
                List.of(new PullRequest(100, 20, "a"), new PullRequest(300, 80, "b")), null));

        assertThat(habits.prCount()).isEqualTo(2);
        assertThat(habits.avgPrSize()).isEqualTo(250.0);
    }
}
