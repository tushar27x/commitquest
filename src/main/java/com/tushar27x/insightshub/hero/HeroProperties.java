package com.tushar27x.insightshub.hero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code insightshub.hero.*}. The hourly job's schedule, {@code insightshub.hero.cron}, is read by
 * {@code @Scheduled} in {@link HeroJob} directly.
 *
 * @param concurrency   heroes refreshed and settled at once (each refresh is one or two GitHub calls on the
 *                      user's own token)
 * @param qualifyScore  a class with at least this score can be chosen (the top class always can)
 * @param classCooldown time between class changes
 */
@ConfigurationProperties(prefix = "insightshub.hero")
public record HeroProperties(@DefaultValue("4") int concurrency,
                             @DefaultValue("40") int qualifyScore,
                             @DefaultValue("7d") Duration classCooldown) {

    public HeroProperties {
        if (concurrency < 1) {
            throw new IllegalArgumentException("insightshub.hero.concurrency must be at least 1");
        }
    }
}
