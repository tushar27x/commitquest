package com.tushar27x.insightshub.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** A {@link ChatModel} that answers from a function and records every prompt it receives. */
final class FakeChatModel implements ChatModel {

    static final String PERSONA = "PERSONA";

    final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private final Function<Prompt, String> answer;
    private final ChatResponseMetadata metadata;

    private FakeChatModel(Function<Prompt, String> answer) {
        this(answer, new ChatResponseMetadata());
    }

    private FakeChatModel(Function<Prompt, String> answer, ChatResponseMetadata metadata) {
        this.answer = answer;
        this.metadata = metadata;
    }

    /** Also reports token usage, like a real provider. */
    static FakeChatModel answeringWithUsage(String text, int promptTokens, int completionTokens) {
        return new FakeChatModel(prompt -> text, ChatResponseMetadata.builder()
                .usage(new DefaultUsage(promptTokens, completionTokens)).build());
    }

    static FakeChatModel answering(String text) {
        return new FakeChatModel(prompt -> text);
    }

    static FakeChatModel answering(Function<Prompt, String> answer) {
        return new FakeChatModel(answer);
    }

    static FakeChatModel failing() {
        return new FakeChatModel(prompt -> {
            throw new IllegalStateException("provider down");
        });
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer.apply(prompt)))), metadata);
    }

    Prompt last() {
        return prompts.getLast();
    }

    String lastUserText() {
        return last().getUserMessage().getText();
    }

    String lastSystemText() {
        return last().getSystemMessage().getText();
    }

    /** Providers named p0, p1, ... with models "big" / "small", each with the persona as default system prompt. */
    static LlmChain chain(FakeChatModel... models) {
        List<LlmProvider> providers = new java.util.ArrayList<>();
        for (int i = 0; i < models.length; i++) {
            providers.add(new LlmProvider("p" + i,
                    ChatClient.builder(models[i]).defaultSystem(PERSONA).build(), "big", "small", null));
        }
        return new LlmChain(providers);
    }
}
