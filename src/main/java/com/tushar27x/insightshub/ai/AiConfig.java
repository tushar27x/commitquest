package com.tushar27x.insightshub.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
class AiConfig {

    static final int FALLBACK_MAX_RETRIES = 1;
    static final Duration FALLBACK_TIMEOUT = Duration.ofSeconds(20);

    /**
     * Groq (auto-configured from {@code spring.ai.openai.*}) first, then the fallback provider if it has a key.
     * Spring AI auto-configures only one OpenAI model, so the fallback is built by hand.
     */
    @Bean
    LlmChain llmChain(OpenAiChatModel groq, AiProperties properties,
                      @Value("classpath:prompts/persona.st") Resource persona,
                      @Value("${spring.ai.openai.chat.max-retries}") int primaryRetries,
                      @Value("${spring.ai.openai.chat.timeout}") Duration primaryTimeout) {
        List<LlmProvider> providers = new ArrayList<>();
        providers.add(new LlmProvider("groq", client(groq, persona), groq.getOptions().getModel(),
                properties.fastModel(), properties.reasoningEffort()));

        AiProperties.Fallback fallback = properties.fallback();
        if (fallback != null && fallback.enabled()) {
            OpenAiChatModel model = openAiCompatibleModel(fallback.baseUrl(), fallback.apiKey(), fallback.model());
            providers.add(new LlmProvider("fallback", client(model, persona), fallback.model(), null, null));
        }

        LlmChain chain = new LlmChain(providers);
        log.info("AI providers in order: {}", chain.providerNames());

        Duration worstCase = worstCase(primaryRetries, primaryTimeout);
        if (providers.size() > 1) {
            worstCase = worstCase.plus(worstCase(FALLBACK_MAX_RETRIES, FALLBACK_TIMEOUT));
        }
        if (worstCase.compareTo(properties.taskTimeout()) >= 0) {
            log.warn("AI providers can take up to {} per task, but task-timeout is {}: a timed-out task's "
                    + "HTTP calls keep running in the background. Lower the provider timeouts.",
                    worstCase, properties.taskTimeout());
        }
        return chain;
    }

    /**
     * Longest one provider can take: every attempt runs into its timeout. Cancelling the task's future
     * wouldn't stop a blocking HTTP call, so instead the timeouts are sized to end before the task timeout.
     */
    static Duration worstCase(int retries, Duration timeout) {
        return timeout.multipliedBy(retries + 1L);
    }

    /** A chat model for any OpenAI-compatible endpoint (Gemini, Groq, ...), failing fast like the primary. */
    static OpenAiChatModel openAiCompatibleModel(String baseUrl, String apiKey, String model) {
        return OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .model(model)
                        .maxRetries(FALLBACK_MAX_RETRIES)
                        .timeout(FALLBACK_TIMEOUT)
                        .build())
                .build();
    }

    static ChatClient client(OpenAiChatModel model, Resource persona) {
        return ChatClient.builder(model).defaultSystem(persona).build();
    }
}
