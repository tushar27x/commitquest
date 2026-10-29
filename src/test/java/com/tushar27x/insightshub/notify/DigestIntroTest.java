package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.ai.AiProperties;
import com.tushar27x.insightshub.ai.FakeChatModel;
import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;
import com.tushar27x.insightshub.hero.HeroState;
import com.tushar27x.insightshub.notify.DigestIntro.Intro;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.tushar27x.insightshub.ai.FakeChatModel.chain;
import static org.assertj.core.api.Assertions.assertThat;

/** The intro is the LLM's when it answers usably, else the static one; it never throws. */
class DigestIntroTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 26);
    private static final HeroState HERO = new HeroState(2, 10, 1, 0, MON.plusDays(1));
    private static final DigestContent CONTENT = DigestContent.of(List.of(
            new HeroEvent(HeroEventType.RESTED, MON, Map.of()),
            new HeroEvent(HeroEventType.LIFE_LOST, MON.plusDays(1), Map.of("lives", 1, "reason", "NO_ACTIVITY"))));

    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    private DigestIntro intro(FakeChatModel model, Duration timeout) {
        return new DigestIntro(chain(model), executor, new AiProperties(null, null, timeout, null));
    }

    @Test
    void asksTheLlmWithTheHeroAndTheEvents() {
        FakeChatModel model = FakeChatModel.answering("  One commit today and the streak starts again.  ");

        Intro written = intro(model, Duration.ofSeconds(5)).write("octocat", CONTENT, HERO);

        assertThat(written).isEqualTo(new Intro("One commit today and the streak starts again.", true));
        assertThat(model.lastUserText())
                .contains("octocat")
                .contains("level 2, 1 heart, streak 0")
                .contains("- Mon 26 Oct: A rest day: your hero rested and lost nothing.\n"
                        + "- Tue 27 Oct: A quiet day cost a heart: 1 heart left.");
    }

    @Test
    void everyProviderDownGivesTheStaticIntro() {
        Intro written = intro(FakeChatModel.failing(), Duration.ofSeconds(5)).write("octocat", CONTENT, HERO);

        assertThat(written).isEqualTo(new Intro(DigestContent.INTRO_SETBACK, false));
    }

    @Test
    void aRamblingAnswerGivesTheStaticIntro() {
        FakeChatModel model = FakeChatModel.answering("x".repeat(DigestIntro.MAX_CHARS + 1));

        assertThat(intro(model, Duration.ofSeconds(5)).write("octocat", CONTENT, HERO).fromAi()).isFalse();
    }

    @Test
    void aSlowLlmGivesTheStaticIntroInTime() {
        FakeChatModel slow = FakeChatModel.answering(prompt -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "too late";
        });

        long start = System.nanoTime();
        Intro written = intro(slow, Duration.ofMillis(100)).write("octocat", CONTENT, HERO);

        assertThat(written.fromAi()).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void noHeroIsDescribedAsUnknown() {
        assertThat(DigestIntro.describe(null)).isEqualTo("(unknown)");
        assertThat(DigestIntro.describe(new HeroState(4, 0, 3, 12, MON))).isEqualTo("level 4, 3 hearts, streak 12");
    }
}
