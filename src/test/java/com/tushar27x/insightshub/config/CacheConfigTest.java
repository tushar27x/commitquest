package com.tushar27x.insightshub.config;

import com.tushar27x.insightshub.ai.CharacterSheet;
import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.user.InsightsQueryService;
import com.tushar27x.insightshub.user.InsightsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilder;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Redis cache settings and the JSON round trip, without a Redis server. */
class CacheConfigTest {

    private static final InsightsResponse RESPONSE = new InsightsResponse(
            "octocat", Archetype.MONK, Map.of("MONK", 52, "ROGUE", 47),
            Map.of("total_commits", 42, "top_languages", List.of("Java", "Go")),
            "year", "week", "craft", Map.of("app", "An app."),
            new CharacterSheet("octocat the Monk", "Calm."), Instant.parse("2026-10-14T12:00:00Z"));

    @Test
    void insightsSurviveTheJsonRoundTrip() {
        RedisSerializer<InsightsResponse> serializer = CacheConfig.insightsSerializer();

        byte[] stored = serializer.serialize(RESPONSE);

        assertThat(new String(stored, StandardCharsets.UTF_8))
                .contains("\"user_name\":\"octocat\"", "\"year_in_review\"", "\"character_sheet\"")
                .doesNotContain("@class"); // plain JSON, no Java class names
        assertThat(serializer.deserialize(stored)).isEqualTo(RESPONSE);
    }

    @Test
    void insightsCacheHasTtlAndItsOwnPrefix() {
        RedisCacheManagerBuilder builder = RedisCacheManagerBuilder.fromConnectionFactory(mock(RedisConnectionFactory.class));

        new CacheConfig().insightsCache().customize(builder);
        RedisCacheConfiguration config = builder.getCacheConfigurationFor(InsightsQueryService.CACHE).orElseThrow();

        assertThat(config.getTtlFunction().getTimeToLive(583231L, RESPONSE)).isEqualTo(Duration.ofHours(24));
        assertThat(config.getKeyPrefixFor(InsightsQueryService.CACHE)).isEqualTo("v2:user_insights::");
        assertThat(config.getAllowCacheNullValues()).isFalse();
    }
}
