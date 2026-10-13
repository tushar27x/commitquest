package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import com.tushar27x.insightshub.insights.UserStats.RepoDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.tushar27x.insightshub.insights.GitHubTestData.day;
import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedArchetypeClassifierTest {

    private final RuleBasedArchetypeClassifier classifier = new RuleBasedArchetypeClassifier();

    // 2026-10-10 Sat, 2026-10-11 Sun, 2026-10-12 Mon, 2026-10-13 Tue
    private static final List<ContributionDay> WEEKDAY_HEAVY =
            List.of(day("2026-10-10", 1), day("2026-10-12", 5), day("2026-10-13", 5));

    private static UserStats stats(int commits, int prs, int issues, List<String> files) {
        return new UserStats(commits, prs, issues, List.of(),
                List.of(new RepoDetail("repo", "", files)),
                List.of(), new UserStats.CodeChurn(0, 0), List.of(), 0, 0, "0/0",
                List.of(), List.of(), List.of());
    }

    @Test
    void bugHunterWhenIssuesExceedTwentyPercentOfCodeOutput() {
        // 21 > (80 + 20) * 0.2 = 20
        assertThat(classifier.classify(stats(80, 20, 21, List.of()), WEEKDAY_HEAVY))
                .isEqualTo(Archetype.BUG_HUNTER);
    }

    @Test
    void notBugHunterAtExactlyTwentyPercent() {
        assertThat(classifier.classify(stats(80, 20, 20, List.of()), WEEKDAY_HEAVY))
                .isNotEqualTo(Archetype.BUG_HUNTER);
    }

    @Test
    void openSourceHeroWhenPullRequestsExceedFortyPercentOfCommits() {
        // 41 > 100 * 0.4, and issues are low
        assertThat(classifier.classify(stats(100, 41, 0, List.of()), WEEKDAY_HEAVY))
                .isEqualTo(Archetype.OPEN_SOURCE_HERO);
    }

    @Test
    void bugHunterBeatsOpenSourceHero() {
        assertThat(classifier.classify(stats(10, 50, 50, List.of()), WEEKDAY_HEAVY))
                .isEqualTo(Archetype.BUG_HUNTER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Dockerfile", "docker-compose.yml", "k8s", "main.TF.terraform", "tsconfig.json", ".workflow"})
    void softwareArchitectWhenAnyFileContainsAnInfraSignal(String file) {
        assertThat(classifier.classify(stats(100, 0, 0, List.of("README.md", file)), WEEKDAY_HEAVY))
                .isEqualTo(Archetype.SOFTWARE_ARCHITECT);
    }

    @Test
    void openSourceHeroBeatsSoftwareArchitect() {
        assertThat(classifier.classify(stats(10, 10, 0, List.of("Dockerfile")), WEEKDAY_HEAVY))
                .isEqualTo(Archetype.OPEN_SOURCE_HERO);
    }

    @Test
    void weekendWarriorWhenWeekendExceedsHalfOfWeekday() {
        // weekend 6 (Sat 3 + Sun 3) > weekday 10 * 0.5
        List<ContributionDay> days = List.of(
                day("2026-10-10", 3), day("2026-10-11", 3), day("2026-10-12", 5), day("2026-10-13", 5));

        assertThat(classifier.classify(stats(100, 0, 0, List.of("src")), days))
                .isEqualTo(Archetype.WEEKEND_WARRIOR);
    }

    @Test
    void codeCrusaderOtherwise() {
        assertThat(classifier.classify(stats(100, 0, 0, List.of("src", "README.md")), WEEKDAY_HEAVY))
                .isEqualTo(Archetype.CODE_CRUSADER);
    }

    @Test
    void inactiveUserIsCodeCrusader() {
        // every comparison is 0 > 0, so no rule fires
        assertThat(classifier.classify(stats(0, 0, 0, List.of()), List.of()))
                .isEqualTo(Archetype.CODE_CRUSADER);
    }
}
