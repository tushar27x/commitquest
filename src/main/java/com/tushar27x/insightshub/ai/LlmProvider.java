package com.tushar27x.insightshub.ai;

import org.springframework.ai.chat.client.ChatClient;

/**
 * One LLM provider: a {@link ChatClient} (with the manager persona as its default system prompt)
 * and the model names to use per {@link ModelTier}.
 *
 * @param reasoningEffort for reasoning models ({@code low}, {@code medium}, {@code high}); null for models
 *                        that don't reason, which may reject the parameter
 */
public record LlmProvider(String name, ChatClient client, String model, String fastModel, String reasoningEffort) {

    public String model(ModelTier tier) {
        return tier == ModelTier.FAST && fastModel != null && !fastModel.isBlank() ? fastModel : model;
    }
}
