package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * v1's {@code determine_archetype}: the first matching rule wins, in this priority order.
 */
@Component
public class RuleBasedArchetypeClassifier implements ArchetypeClassifier {

    /** Substrings of root file names that suggest infrastructure work (v1 list, substring match). */
    static final Set<String> ARCHITECT_SIGNALS =
            Set.of("docker", "kubernetes", "k8s", "terraform", "yml", "yaml", "config", "workflow");

    @Override
    public Archetype classify(UserStats stats, List<ContributionDay> days) {
        int commits = stats.totalCommits();
        int prs = stats.totalPrs();
        int issues = stats.totalIssues();

        // 1. Reports issues a lot relative to code output
        if (issues > (commits + prs) * 0.2) {
            return Archetype.BUG_HUNTER;
        }
        // 2. Works through pull requests a lot relative to direct commits
        if (prs > commits * 0.4) {
            return Archetype.OPEN_SOURCE_HERO;
        }
        // 3. Infrastructure files in any top repository
        if (hasArchitectSignal(stats)) {
            return Archetype.SOFTWARE_ARCHITECT;
        }
        // 4. Weekend contributions more than half of weekday contributions
        int weekend = 0;
        int weekday = 0;
        for (ContributionDay day : days) {
            if (isWeekend(day.date().getDayOfWeek())) {
                weekend += day.contributionCount();
            } else {
                weekday += day.contributionCount();
            }
        }
        if (weekend > weekday * 0.5) {
            return Archetype.WEEKEND_WARRIOR;
        }
        return Archetype.CODE_CRUSADER;
    }

    private static boolean hasArchitectSignal(UserStats stats) {
        return stats.repoDetails().stream()
                .flatMap(repo -> repo.files().stream())
                .map(file -> file.toLowerCase(Locale.ROOT))
                .anyMatch(file -> ARCHITECT_SIGNALS.stream().anyMatch(file::contains));
    }

    private static boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
