package com.tushar27x.insightshub.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code insightshub.ai.*}. The primary provider (Groq) is configured through {@code spring.ai.openai.*}.
 *
 * @param fastModel       primary-provider model for cheap tasks (the digest intro)
 * @param reasoningEffort primary-provider reasoning effort (its models are reasoning models)
 * @param taskTimeout     upper bound for one AI task (the combined texts call, or a digest intro), including the
 *                        fallback provider
 * @param fallback        optional second OpenAI-compatible provider, used when the primary fails
 * @param regenerate      when the daily job rewrites a user's texts (B11)
 */
@ConfigurationProperties(prefix = "insightshub.ai")
public record AiProperties(
        String fastModel,
        String reasoningEffort,
        @DefaultValue("90s") Duration taskTimeout,
        Fallback fallback,
        @DefaultValue Regenerate regenerate) {

    /** Without the regeneration rules: their defaults. */
    public AiProperties(String fastModel, String reasoningEffort, Duration taskTimeout, Fallback fallback) {
        this(fastModel, reasoningEffort, taskTimeout, fallback, Regenerate.DEFAULTS);
    }

    @ConstructorBinding // two constructors: Spring binds this one (the convenience one below hid it, B44)
    public AiProperties {
        if (regenerate == null) {
            regenerate = Regenerate.DEFAULTS;
        }
    }

    /** Enabled only when {@code apiKey} is set. */
    public record Fallback(String baseUrl, String apiKey, String model) {

        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * {@code insightshub.ai.regenerate.*}: the thresholds of {@link Regeneration}.
     *
     * @param scoreDelta  points any class score must move by
     * @param statChange  share by which total commits, PRs or stars must change (0.10 = 10%)
     * @param maxAge      texts this old are rewritten even if nothing moved; whole UTC days
     * @param concurrency LLM calls the daily job makes at once (each user's GitHub work isn't limited by this)
     */
    public record Regenerate(@DefaultValue("5") int scoreDelta,
                             @DefaultValue("0.10") double statChange,
                             @DefaultValue("7d") Duration maxAge,
                             @DefaultValue("2") int concurrency) {

        public static final Regenerate DEFAULTS = new Regenerate(5, 0.10, Duration.ofDays(7), 2);

        public Regenerate {
            if (scoreDelta < 1 || statChange <= 0 || maxAge.toDays() < 1 || concurrency < 1) {
                throw new IllegalArgumentException("insightshub.ai.regenerate: score-delta, stat-change and "
                        + "concurrency must be positive, max-age at least 1d");
            }
        }
    }
}
