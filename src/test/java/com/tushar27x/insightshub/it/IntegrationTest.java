package com.tushar27x.insightshub.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The full application context against Testcontainers Postgres + Redis ({@link TestcontainersConfig}).
 * Name these test classes {@code *IT}: Failsafe runs them in {@code ./mvnw verify}, while
 * {@code ./mvnw test} stays unit-only. All of them share one context, so the containers start once.
 * <p>
 * They also share one database, so each class owns a block of 100 GitHub ids for its test users and
 * stays inside it ({@code USER + n} included): {@code DailyContributionsRepositoryIT} 9_000_0xx,
 * {@code UsersRepositoryIT} 9_000_1xx, {@code HeroSchemaIT} 9_000_2xx,
 * {@code HeroRepositoryIT} 9_000_3xx. A new class takes the next free
 * block and adds itself here. Clean up your own block in {@code @BeforeEach}, never other rows.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(TestcontainersConfig.class)
public @interface IntegrationTest {
}
