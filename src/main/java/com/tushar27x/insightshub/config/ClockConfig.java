package com.tushar27x.insightshub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Single source of "now" for the app. Inject {@link Clock} instead of calling
 * {@code Instant.now()} so time-dependent code can be tested with a fixed clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
