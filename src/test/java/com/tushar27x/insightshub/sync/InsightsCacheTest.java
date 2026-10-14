package com.tushar27x.insightshub.sync;

import com.tushar27x.insightshub.ai.AiInsights;
import com.tushar27x.insightshub.ai.AiTestData;
import com.tushar27x.insightshub.ai.CharacterSheet;
import com.tushar27x.insightshub.entity.Insights;
import com.tushar27x.insightshub.entity.Users;
import com.tushar27x.insightshub.repository.InsightsRepository;
import com.tushar27x.insightshub.repository.UsersRepository;
import com.tushar27x.insightshub.user.InsightsQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The caching annotations on the real beans, through Spring's proxies, with an in-memory cache standing
 * in for Redis (the Redis-specific settings are in {@code CacheConfigTest}). No DB, no network.
 */
@SpringJUnitConfig(InsightsCacheTest.Config.class)
class InsightsCacheTest {

    private static final long ID = 583231L;

    @Configuration
    @EnableCaching
    @Import({InsightsQueryService.class, InsightsWriter.class})
    static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(InsightsQueryService.CACHE);
        }
    }

    @Autowired
    private InsightsQueryService queries;
    @Autowired
    private InsightsWriter writer;
    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private InsightsRepository insightsRepository;
    @MockitoBean
    private UsersRepository usersRepository;

    private final Users user = new Users(ID, "octocat");

    @BeforeEach
    void setUp() {
        cache().clear();
        when(usersRepository.findById(ID)).thenReturn(Optional.of(user));
    }

    @Test
    void secondReadIsServedFromTheCache() {
        when(insightsRepository.findById(ID)).thenReturn(Optional.of(new Insights(user)));

        assertThat(queries.getInsights(ID)).isPresent();
        assertThat(queries.getInsights(ID)).isPresent();

        verify(insightsRepository, times(1)).findById(ID);
        assertThat(cache().get(ID)).isNotNull();
    }

    @Test
    void noInsightsYetIsNotCached() {
        when(insightsRepository.findById(ID)).thenReturn(Optional.empty());

        assertThat(queries.getInsights(ID)).isEmpty();
        assertThat(queries.getInsights(ID)).isEmpty();

        verify(insightsRepository, times(2)).findById(ID);
        assertThat(cache().get(ID)).isNull();
    }

    @Test
    void aSyncEvictsOnlyThatUsersEntry() {
        when(insightsRepository.findById(ID)).thenReturn(Optional.of(new Insights(user)));
        queries.getInsights(ID);
        cache().put(99L, "someone else");

        writer.save(ID, Map.of(), AiTestData.insights(),
                new AiInsights("y", "w", "c", Map.of(), new CharacterSheet("t", "l")), Instant.now());

        assertThat(cache().get(ID)).isNull();
        assertThat(cache().get(99L)).isNotNull();
    }

    private Cache cache() {
        return cacheManager.getCache(InsightsQueryService.CACHE);
    }
}
