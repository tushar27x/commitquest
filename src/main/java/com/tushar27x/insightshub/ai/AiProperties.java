package com.tushar27x.insightshub.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code insightshub.ai.*}. The primary provider (Groq) is configured through {@code spring.ai.openai.*}.
 *
 * @param fastModel   primary-provider model for cheap tasks (repo summaries)
 * @param reasoningEffort primary-provider reasoning effort (its models are reasoning models)
 * @param taskTimeout upper bound for one AI task, including the fallback provider
 * @param fallback    optional second OpenAI-compatible provider, used when the primary fails
 */
@ConfigurationProperties(prefix = "insightshub.ai")
public record AiProperties(
        String fastModel,
        String reasoningEffort,
        @DefaultValue("90s") Duration taskTimeout,
        Fallback fallback) {

    /** Enabled only when {@code apiKey} is set. */
    public record Fallback(String baseUrl, String apiKey, String model) {

        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
