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
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@Import(TestcontainersConfig.class)
public @interface IntegrationTest {
}
