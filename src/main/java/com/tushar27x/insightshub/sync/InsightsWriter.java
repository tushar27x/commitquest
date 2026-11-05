package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInputs;
import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.Regeneration;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

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

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final UsersRepository usersRepository;
    private final InsightsRepository insightsRepository;
    private final JsonMapper jsonMapper;

    public InsightsWriter(UsersRepository usersRepository, InsightsRepository insightsRepository,
                          JsonMapper jsonMapper) {
        this.usersRepository = usersRepository;
        this.insightsRepository = insightsRepository;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Upserts the insights row with freshly written AI texts, and stamps {@code users.last_synced_at}, in one
     * transaction. The texts count as written from {@code result} at {@code syncedAt}.
     * The cache entry is evicted after the commit (the cache manager is transaction-aware).
     */
    @Transactional
    @CacheEvict(cacheNames = InsightsQueryService.CACHE, key = "#userId")
    public void save(long userId, Map<String, Object> stats, InsightsResult result, AiInsights ai, Instant syncedAt) {
        write(userId, stats, result, new GeneratedTexts(ai, AiInputs.of(result), syncedAt), syncedAt);
    }

    /**
     * Like {@link #save}, but the stored AI texts, their timestamp and inputs stay as they are (B11: later logins,
     * SAVE, and daily runs where nothing moved). Stats, class and scores are rewritten.
     */
    @Transactional
    @CacheEvict(cacheNames = InsightsQueryService.CACHE, key = "#userId")
    public void saveKeepingTexts(long userId, Map<String, Object> stats, InsightsResult result, Instant syncedAt) {
        write(userId, stats, result, null, syncedAt);
    }

    private void write(long userId, Map<String, Object> stats, InsightsResult result, GeneratedTexts texts,
                       Instant syncedAt) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User " + userId + " disappeared during sync"));
        Insights insights = insightsRepository.findById(userId).orElseGet(() -> new Insights(user)); // B18

        insights.setStats(stats);
        insights.setArchetype(result.archetype());
        insights.setArchetypeScores(byName(result.archetypeScores().scores()));
        insights.setAlgoVersion(result.archetypeScores().algoVersion());
        if (texts != null) {
            AiInsights ai = texts.texts();
            insights.setYearInReview(ai.yearInReview());
            insights.setWeeklyReview(ai.weeklyReview());
            insights.setCraftReview(ai.craftReview());
            insights.setRepoSummaries(new LinkedHashMap<>(ai.repoSummaries()));
            insights.setCharacterTitle(ai.characterSheet().title());
            insights.setCharacterLore(ai.characterSheet().lore());
            insights.setAiGeneratedAt(texts.generatedAt());
            insights.setAiInputs(jsonMapper.convertValue(texts.inputs(), MAP));
            insights.setAiFallback(ai.fallback());
        }
        insightsRepository.save(insights);

        user.setLastSyncedAt(syncedAt); // managed entity: flushed on commit
    }

    /** What is stored about the user's texts, for {@link Regeneration}. Read without a transaction. */
    public Regeneration.Stored storedTexts(long userId) {
        return insightsRepository.findById(userId)
                .map(this::stored)
                .orElse(Regeneration.Stored.NONE);
    }

    private Regeneration.Stored stored(Insights insights) {
        AiInputs inputs = null;
        if (insights.getAiInputs() != null) {
            try {
                inputs = jsonMapper.convertValue(insights.getAiInputs(), AiInputs.class);
            } catch (RuntimeException e) {
                inputs = null; // unreadable: treated like texts from before V7, rewritten once
            }
        }
        return new Regeneration.Stored(insights.getYearInReview() != null, insights.getAiGeneratedAt(), inputs,
                insights.isAiFallback());
    }

    /** AI texts and what they were written from. */
    record GeneratedTexts(AiInsights texts, AiInputs inputs, Instant generatedAt) {
    }

    /**
     * GitHub rejected the stored token: stop syncing this user until they log in again (Phase 10). Only if
     * {@code rejectedToken} (encrypted, as read before the GitHub call) is still the stored one, so a login in the
     * meantime isn't undone (B45).
     *
     * @return whether the user was flagged
     */
    public boolean markNeedsReauth(long userId, String rejectedToken) {
        return usersRepository.markNeedsReauth(userId, rejectedToken) == 1;
    }

    private static Map<String, Integer> byName(Map<Archetype, Integer> scores) {
        Map<String, Integer> byName = new LinkedHashMap<>();
        scores.forEach((archetype, score) -> byName.put(archetype.name(), score));
        return byName;
    }
}
