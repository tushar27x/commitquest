package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.ai.AiProperties;
import com.tushar27x.insightshub.ai.LlmChain;
import com.tushar27x.insightshub.ai.ModelTier;
import com.tushar27x.insightshub.hero.HeroState;
import com.tushar27x.insightshub.notify.DigestContent.Line;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The digest's intro: one fast-tier LLM call in the mentor persona (the providers' default system prompt), or
 * {@link DigestContent#fallbackIntro()} if every provider fails, the answer is unusable, or it takes longer
 * than {@code insightshub.ai.task-timeout}. Never throws: the email goes out on an AI outage (plan §12).
 * <p>
 * Since #32 this is the only scheduled LLM use: at most one call per mailed user per day, plus a retry per
 * hour while their email fails to send.
 */
@Slf4j
@Component
public class DigestIntro {

    static final int MAX_CHARS = 600;
    private static final Resource PROMPT = new ClassPathResource("prompts/digest-intro.st");

    private final LlmChain llm;
    private final Executor executor;
    private final Duration timeout;

    public DigestIntro(LlmChain llm, @Qualifier("applicationTaskExecutor") Executor executor, AiProperties properties) {
        this.llm = llm;
        this.executor = executor;
        this.timeout = properties.taskTimeout();
    }

    public Intro write(String login, DigestContent content, HeroState hero) {
        Intro fallback = new Intro(content.fallbackIntro(), false);
        return CompletableFuture.supplyAsync(() -> ask(login, content, hero).orElse(fallback), executor)
                .completeOnTimeout(fallback, timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(e -> {
                    log.error("Digest intro for '{}' crashed; using the static one", login, e);
                    return fallback;
                })
                .join();
    }

    private Optional<Intro> ask(String login, DigestContent content, HeroState hero) {
        String events = Stream.concat(content.headlines().stream(), content.also().stream())
                .sorted(Comparator.comparing(Line::onDate))
                .map(line -> "- " + line.day() + ": " + line.text())
                .collect(Collectors.joining("\n"));
        return llm.text("digest intro", ModelTier.FAST, 120, spec -> spec.user(u -> u.text(PROMPT)
                        .param("login", login)
                        .param("hero", describe(hero))
                        .param("events", events)))
                .filter(text -> text.length() <= MAX_CHARS)
                .map(text -> new Intro(text, true));
    }

    static String describe(HeroState hero) {
        if (hero == null) {
            return "(unknown)";
        }
        return "level " + hero.level() + ", " + hero.lives() + (hero.lives() == 1 ? " heart" : " hearts")
                + ", streak " + hero.streak();
    }

    /** @param fromAi false: the static fallback */
    public record Intro(String text, boolean fromAi) {
    }
}
