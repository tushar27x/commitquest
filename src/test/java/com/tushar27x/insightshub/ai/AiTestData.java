package com.tushar27x.insightshub.ai;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.insights.ArchetypeScores;
import com.tushar27x.insightshub.insights.HabitProfile;
import com.tushar27x.insightshub.insights.InsightsResult;
import com.tushar27x.insightshub.insights.UserStats;
import com.tushar27x.insightshub.insights.UserStats.CodeChurn;
import com.tushar27x.insightshub.insights.UserStats.RepoDetail;

import java.util.List;
import java.util.Map;

final class AiTestData {

    private AiTestData() {
    }

    static UserStats stats(List<String> commits, List<RepoDetail> repos) {
        return new UserStats(42, 7, 3, List.of(), repos, commits, new CodeChurn(120, 30),
                List.of("Java", "Go"), 12, 100, "10/2",
                List.of(0, 3, 0, 1, 0, 5, 2), List.of(), List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"));
    }

    static UserStats stats() {
        return stats(List.of("feat: add parser", "wip"), List.of(new RepoDetail("app", "# App", List.of("pom.xml"))));
    }

    static HabitProfile habits() {
        return new HabitProfile(0.26, 22, 0.44, 6.0, 4, 0.2, 0, 0.02, 1, 0.4, 10, 1546.3, 18, 0.06, 0.83);
    }

    static ArchetypeScores scores() {
        return new ArchetypeScores(Archetype.MONK, Map.of(
                Archetype.PALADIN, 44, Archetype.BARBARIAN, 44, Archetype.WIZARD, 33, Archetype.ARTIFICER, 33,
                Archetype.CLERIC, 0, Archetype.BARD, 10, Archetype.MONK, 52, Archetype.ROGUE, 47), 1);
    }

    static InsightsResult insights() {
        return new InsightsResult(stats(), habits(), scores());
    }
}
