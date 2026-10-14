package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.repository.InsightsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Read side of the insights, cached in Redis (replaces v1's manual {@code redis.get}/{@code set}).
 * The sync evicts the entry ({@code InsightsWriter}), so a cached value is never older than the last sync.
 */
@Slf4j
@Service
public class InsightsQueryService {

    public static final String CACHE = "user_insights";

    private final InsightsRepository insightsRepository;

    public InsightsQueryService(InsightsRepository insightsRepository) {
        this.insightsRepository = insightsRepository;
    }

    /**
     * Spring caches the value inside the {@code Optional}; {@code #result} is that value, so
     * "no insights yet" is not cached and the first sync shows up immediately.
     */
    @Cacheable(cacheNames = CACHE, key = "#userId", unless = "#result == null")
    public Optional<InsightsResponse> getInsights(long userId) {
        log.debug("Cache miss for user {}: reading insights from the database", userId);
        return insightsRepository.findById(userId).map(InsightsResponse::from);
    }
}
