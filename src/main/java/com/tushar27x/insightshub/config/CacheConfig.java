package com.tushar27x.insightshub.config;

import com.tushar27x.insightshub.user.InsightsQueryService;
import com.tushar27x.insightshub.user.InsightsResponse;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

/**
 * Redis-backed Spring Cache. Values are stored as JSON, not Java serialization: readable in
 * {@code redis-cli}, and a renamed class can't make old entries undeserializable.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    static final Duration INSIGHTS_TTL = Duration.ofHours(24);

    /** v1 shares this Redis, so v2's keys get their own prefix: {@code v2:user_insights::<githubId>}. */
    static final String KEY_PREFIX = "v2:";

    @Bean
    RedisCacheManagerBuilderCustomizer insightsCache() {
        return builder -> builder
                // evictions wait for the surrounding transaction to commit, so a reader can't re-cache
                // the old row between the evict and the commit
                .transactionAware()
                .withCacheConfiguration(InsightsQueryService.CACHE, RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(INSIGHTS_TTL)
                        .disableCachingNullValues()
                        .computePrefixWith(cacheName -> KEY_PREFIX + cacheName + "::")
                        .serializeValuesWith(SerializationPair.fromSerializer(insightsSerializer())));
    }

    /**
     * Typed to the record: a generic serializer would have to embed class names in the JSON, and
     * Jackson's default typing skips final classes such as records (they'd come back as maps).
     */
    static RedisSerializer<InsightsResponse> insightsSerializer() {
        return new JacksonJsonRedisSerializer<>(InsightsResponse.class);
    }
}
