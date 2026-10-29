package com.tushar27x.insightshub.it;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Throwaway Postgres and Redis for integration tests, so no test touches the live Neon DB (B23).
 * {@code @ServiceConnection} points Spring at the containers; the rest of the config the app needs
 * comes from {@link #testProperties()}, with secrets generated at runtime (B31).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    /** Neon runs Postgres 17. */
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    @ServiceConnection
    RedisContainer redis() {
        return new RedisContainer(DockerImageName.parse("redis:7-alpine"));
    }

    /**
     * Replaces every {@code ${...}} placeholder in application.yml, so the tests need no {@code .env}
     * and never use real secrets. The LLM base URL points at a closed local port: a test that
     * accidentally calls the AI layer fails fast instead of reaching Groq.
     */
    @Bean
    DynamicPropertyRegistrar testProperties() {
        return registry -> {
            registry.add("spring.datasource.username", () -> "test");
            registry.add("spring.datasource.password", () -> "test");
            registry.add("spring.data.redis.password", () -> "");
            registry.add("spring.data.redis.ssl.enabled", () -> "false");
            registry.add("spring.ai.openai.api-key", () -> randomBase64(24));
            registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:9/v1");
            registry.add("insightshub.frontend-url", () -> "http://localhost:3000");
            registry.add("insightshub.backend-url", () -> "http://localhost:8080");
            registry.add("insightshub.jwt-secret", () -> randomBase64(48));
            registry.add("insightshub.encryption-key", () -> randomBase64(32));
            registry.add("insightshub.auth.github-client-id", () -> "test-client");
            registry.add("insightshub.auth.github-client-secret", () -> randomBase64(24));
            // The daily sync (B3) is off: its 03:00 UTC cron must not fire in the middle of a test run.
            registry.add("insightshub.sync.cron", () -> "-");
            registry.add("insightshub.hero.cron", () -> "-"); // and so is the hourly hero job (B5)
            registry.add("insightshub.digest.cron", () -> "-"); // and the hourly digest (B7); DigestIT calls it directly
        };
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private static String randomBase64(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getEncoder().encodeToString(value);
    }
}
