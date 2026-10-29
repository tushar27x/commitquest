package com.tushar27x.insightshub.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.LocalTime;

/**
 * {@code insightshub.digest.*}. The job's schedule, {@code insightshub.digest.cron}, is read by {@code @Scheduled}
 * in {@link DigestJob} directly.
 *
 * @param enabled     off by default: the app boots and runs without SMTP; on, it needs {@code spring.mail.host}
 *                    and {@code from}
 * @param sendHour    a user is due from this hour of their local day (0–23)
 * @param concurrency users processed at once (one LLM call and one email each)
 * @param from        the sender address, e.g. {@code Insight Hub <digest@example.com>}
 */
@ConfigurationProperties(prefix = "insightshub.digest")
public record DigestProperties(@DefaultValue("false") boolean enabled,
                               @DefaultValue("8") int sendHour,
                               @DefaultValue("4") int concurrency,
                               @DefaultValue("") String from) {

    public DigestProperties {
        if (sendHour < 0 || sendHour > 23) {
            throw new IllegalArgumentException("insightshub.digest.send-hour must be 0-23");
        }
        if (concurrency < 1) {
            throw new IllegalArgumentException("insightshub.digest.concurrency must be at least 1");
        }
        if (enabled && from.isBlank()) {
            throw new IllegalArgumentException("insightshub.digest.from is required when the digest is enabled");
        }
    }

    public LocalTime sendAt() {
        return LocalTime.of(sendHour, 0);
    }
}
