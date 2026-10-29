package com.tushar27x.insightshub.it;

import com.tushar27x.insightshub.InsightshubApplication;
import org.springframework.boot.SpringApplication;

/**
 * The backend for the I5 full-stack smoke test ({@code e2e/}): the real app on Testcontainers
 * Postgres + Redis ({@link TestcontainersConfig}), with GitHub answered in-process ({@link GitHubStub})
 * and the LLM on a closed port, so every AI text is its static fallback. Never Neon, GitHub or Groq.
 * <p>
 * Started by Playwright ({@code e2e/playwright.config.ts}), or by hand:
 * <pre>
 * ./mvnw spring-boot:test-run -Dspring-boot.run.main-class=com.tushar27x.insightshub.it.E2eBackend \
 *   -Dspring-boot.run.arguments="--server.port=18081 --e2e.backend-url=http://127.0.0.1:18081 --e2e.frontend-url=http://127.0.0.1:3202"
 * </pre>
 * Not a test class (no {@code *IT} or {@code *Test} name), so neither Surefire nor Failsafe runs it.
 */
public final class E2eBackend {

    private E2eBackend() {
    }

    public static void main(String[] args) {
        // B23 guard, as in Surefire/Failsafe: if the containers were ever skipped, fail to connect
        // instead of reaching the Neon DB or Redis from .env. @ServiceConnection overrides these.
        System.setProperty("spring.datasource.url", "jdbc:postgresql://neon-blocked.invalid:5432/blocked");
        System.setProperty("spring.data.redis.host", "redis-blocked.invalid");
        SpringApplication.from(InsightshubApplication::main)
                .with(TestcontainersConfig.class, GitHubStub.class)
                .run(args);
    }
}
