package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;

import java.util.List;

/**
 * Picks a user's archetype. The rule-based implementation is v1's logic; Phase 13 replaces it
 * with per-archetype scores, which is why it sits behind an interface.
 */
public interface ArchetypeClassifier {

    /**
     * @param stats computed stats (totals, repo file names, ...)
     * @param days  the contribution calendar, sorted by date
     */
    Archetype classify(UserStats stats, List<ContributionDay> days);
}
