package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.entity.Insights;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.insights.InsightsResult;
import com.tushar27x.insightshub.repository.InsightsRepository;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.user.InsightsQueryService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The short, transactional end of a sync. A separate bean on purpose: {@code @Transactional} and
 * {@code @CacheEvict} work through a Spring proxy around the bean, and a call from inside the same
 * class ({@code this.save(...)}) skips the proxy, so neither would happen.
 */
@Component
public class InsightsWriter {

    private final UsersRepository usersRepository;
    private final InsightsRepository insightsRepository;

    public InsightsWriter(UsersRepository usersRepository, InsightsRepository insightsRepository) {
        this.usersRepository = usersRepository;
        this.insightsRepository = insightsRepository;
    }

    /**
     * Upserts the insights row and stamps {@code users.last_synced_at}, in one transaction.
     * The cache entry is evicted after the commit (the cache manager is transaction-aware).
     */
    @Transactional
    @CacheEvict(cacheNames = InsightsQueryService.CACHE, key = "#userId")
    public void save(long userId, Map<String, Object> stats, InsightsResult result, AiInsights ai, Instant syncedAt) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User " + userId + " disappeared during sync"));
        Insights insights = insightsRepository.findById(userId).orElseGet(() -> new Insights(user)); // B18

        insights.setStats(stats);
        insights.setArchetype(result.archetype());
        insights.setArchetypeScores(byName(result.archetypeScores().scores()));
        insights.setAlgoVersion(result.archetypeScores().algoVersion());
        insights.setYearInReview(ai.yearInReview());
        insights.setWeeklyReview(ai.weeklyReview());
        insights.setCraftReview(ai.craftReview());
        insights.setRepoSummaries(new LinkedHashMap<>(ai.repoSummaries()));
        insights.setCharacterTitle(ai.characterSheet().title());
        insights.setCharacterLore(ai.characterSheet().lore());
        insightsRepository.save(insights);

        user.setLastSyncedAt(syncedAt); // managed entity: flushed on commit
    }

    /** GitHub rejected the stored token: stop syncing this user until they log in again (Phase 10). */
    @Transactional
    public void markNeedsReauth(long userId) {
        usersRepository.findById(userId).ifPresent(user -> user.setNeedsReauth(true));
    }

    private static Map<String, Integer> byName(Map<Archetype, Integer> scores) {
        Map<String, Integer> byName = new LinkedHashMap<>();
        scores.forEach((archetype, score) -> byName.put(archetype.name(), score));
        return byName;
    }
}
