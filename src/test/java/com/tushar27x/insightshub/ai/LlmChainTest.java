package com.tushar27x.insightshub.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

import static com.tushar27x.insightshub.ai.FakeChatModel.chain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmChainTest {

    @Test
    void firstProviderAnswersAndTheNextIsNotCalled() {
        FakeChatModel primary = FakeChatModel.answering("  from primary \n");
        FakeChatModel fallback = FakeChatModel.answering("from fallback");

        var answer = chain(primary, fallback).text("t", ModelTier.STANDARD, 100, spec -> spec.user("hi"));

        assertThat(answer).contains("from primary");
        assertThat(fallback.prompts).isEmpty();
    }

    @Test
    void failingProviderFallsThroughToTheNext() {
        FakeChatModel fallback = FakeChatModel.answering("from fallback");

        var answer = chain(FakeChatModel.failing(), fallback).text("t", ModelTier.STANDARD, 100, spec -> spec.user("hi"));

        assertThat(answer).contains("from fallback");
        assertThat(fallback.lastUserText()).isEqualTo("hi");
    }

    @Test
    void blankAnswerCountsAsAFailure() {
        var answer = chain(FakeChatModel.answering("   "), FakeChatModel.answering("ok"))
                .text("t", ModelTier.STANDARD, 100, spec -> spec.user("hi"));

        assertThat(answer).contains("ok");
    }

    @Test
    void emptyWhenEveryProviderFails() {
        var answer = chain(FakeChatModel.failing(), FakeChatModel.answering(""))
                .text("t", ModelTier.STANDARD, 100, spec -> spec.user("hi"));

        assertThat(answer).isEmpty();
    }

    @Test
    void unusableStructuredAnswerFallsThrough() {
        FakeChatModel primary = FakeChatModel.answering("rejected");
        FakeChatModel fallback = FakeChatModel.answering("accepted");

        var answer = chain(primary, fallback).call("t", ModelTier.STANDARD, 100,
                spec -> spec.user("hi").call().content(), text -> text.equals("accepted"));

        assertThat(answer).contains("accepted");
        assertThat(primary.prompts).hasSize(1);
    }

    @Test
    void sendsTheTiersModelTheTokenCapAndThePersona() {
        FakeChatModel model = FakeChatModel.answering("ok");
        LlmChain chain = chain(model);

        chain.text("t", ModelTier.FAST, 77, spec -> spec.user("hi"));
        assertThat(model.last().getOptions().getModel()).isEqualTo("small");
        assertThat(model.last().getOptions().getMaxTokens()).isEqualTo(77);
        assertThat(model.lastSystemText()).isEqualTo(FakeChatModel.PERSONA);

        chain.text("t", ModelTier.STANDARD, 100, spec -> spec.user("hi"));
        assertThat(model.last().getOptions().getModel()).isEqualTo("big");
    }

    @Test
    void providerWithoutAFastModelUsesItsMainModel() {
        LlmProvider provider = new LlmProvider("gemini", null, "gemini-main", null, null);

        assertThat(provider.model(ModelTier.FAST)).isEqualTo("gemini-main");
    }

    @Test
    void reasoningProvidersGetTheEffortAndExtraTokens() {
        LlmProvider reasoning = new LlmProvider("groq", null, "gpt-oss", null, "low");
        LlmProvider plain = new LlmProvider("gemini", null, "gemini", null, null);

        OpenAiChatOptions withEffort = LlmChain.options(reasoning, ModelTier.STANDARD, 200).build();
        OpenAiChatOptions without = LlmChain.options(plain, ModelTier.STANDARD, 200).build();

        assertThat(withEffort.getReasoningEffort()).isEqualTo("low");
        assertThat(withEffort.getMaxTokens()).isEqualTo(200 + LlmChain.REASONING_HEADROOM);
        assertThat(without.getReasoningEffort()).isNull();
        assertThat(without.getMaxTokens()).isEqualTo(200);
    }

    @Test
    void needsAtLeastOneProvider() {
        assertThatThrownBy(() -> new LlmChain(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
