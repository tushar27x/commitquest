package com.tushar27x.insightshub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} (the daily sync). Boot's scheduler runs on virtual threads, because
 * {@code spring.threads.virtual.enabled} is on.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
