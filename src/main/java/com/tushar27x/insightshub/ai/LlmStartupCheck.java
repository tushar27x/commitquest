package com.tushar27x.insightshub.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * B11: a start (a redeploy) makes no LLM call. Records how many the chain had made by the time the app was ready,
 * and says so loudly if that's not 0; {@code it/AiTextsIT} checks it.
 */
@Slf4j
@Component
public class LlmStartupCheck {

    private final LlmChain llm;
    private volatile long callsAtReady = -1;

    public LlmStartupCheck(LlmChain llm) {
        this.llm = llm;
    }

    @EventListener(ApplicationReadyEvent.class)
    void ready() {
        callsAtReady = llm.calls();
        if (callsAtReady > 0) {
            log.error("{} LLM calls were made while the app started; a start must make none", callsAtReady);
        }
    }

    /** -1 until the app is ready. */
    public long callsAtReady() {
        return callsAtReady;
    }
}
