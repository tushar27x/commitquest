package com.tushar27x.insightshub.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Tries each provider in order (Groq, then the optional fallback) and returns the first usable answer.
 * v1 did this per generator with try/except; here it lives in one place.
 * <p>
 * Never throws: a failed or unusable answer from every provider is an empty {@code Optional}, and the
 * caller substitutes its static fallback text.
 */
@Slf4j
public class LlmChain {

    /**
     * Extra tokens for reasoning models: their hidden reasoning counts against {@code max_tokens}, and with
     * v1's small caps it used the whole budget and returned empty content (BUGS.md B27).
     */
    static final int REASONING_HEADROOM = 300;

    private final List<LlmProvider> providers;

    public LlmChain(List<LlmProvider> providers) {
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("at least one LLM provider is required");
        }
        this.providers = List.copyOf(providers);
    }

    /**
     * @param task      name for the logs
     * @param maxTokens answer length cap (v1's values)
     * @param request   adds the prompt to a prepared request and extracts the answer
     * @param usable    rejects answers that came back but can't be used (blank, wrong shape, ...)
     */
    public <T> Optional<T> call(String task, ModelTier tier, int maxTokens,
                                Function<ChatClientRequestSpec, T> request, Predicate<T> usable) {
        for (LlmProvider provider : providers) {
            try {
                T answer = request.apply(provider.client().prompt().options(options(provider, tier, maxTokens)));
                if (answer != null && usable.test(answer)) {
                    return Optional.of(answer);
                }
                log.warn("AI task '{}': unusable answer from {}", task, provider.name());
            } catch (RuntimeException e) {
                log.warn("AI task '{}' failed on {}: {}", task, provider.name(), e.toString());
            }
        }
        return Optional.empty();
    }

    static OpenAiChatOptions.Builder options(LlmProvider provider, ModelTier tier, int maxTokens) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(provider.model(tier));
        if (provider.reasoningEffort() != null && !provider.reasoningEffort().isBlank()) {
            return options.reasoningEffort(provider.reasoningEffort()).maxTokens(maxTokens + REASONING_HEADROOM);
        }
        return options.maxTokens(maxTokens);
    }

    /** Plain-text answer; blank counts as a failure. */
    public Optional<String> text(String task, ModelTier tier, int maxTokens,
                                 Function<ChatClientRequestSpec, ChatClientRequestSpec> prompt) {
        return call(task, tier, maxTokens, spec -> prompt.apply(spec).call().content(), text -> !text.isBlank())
                .map(String::strip);
    }

    public List<String> providerNames() {
        return providers.stream().map(LlmProvider::name).toList();
    }
}
