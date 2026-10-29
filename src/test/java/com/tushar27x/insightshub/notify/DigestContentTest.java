package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;
import com.tushar27x.insightshub.notify.DigestContent.Line;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DigestContentTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 26);

    private static HeroEvent event(HeroEventType type, LocalDate on, Map<String, Object> details) {
        return new HeroEvent(type, on, details);
    }

    private static HeroEvent lifeLost(int lives, LocalDate on) {
        return event(HeroEventType.LIFE_LOST, on, Map.of("lives", lives, "reason", "NO_ACTIVITY"));
    }

    private static HeroEvent levelUp(int to, LocalDate on) {
        return event(HeroEventType.LEVEL_UP, on, Map.of("from", to - 1, "to", to));
    }

    @Test
    void describesEveryEventType() {
        Map<String, Object> pin = new HashMap<>();
        pin.put("from", "ROGUE");
        pin.put("to", "WIZARD");
        pin.put("chosen", null);

        assertThat(DigestContent.describe(levelUp(4, MON))).isEqualTo("Your hero reached level 4.");
        assertThat(DigestContent.describe(event(HeroEventType.LEVEL_DOWN, MON, Map.of("from", 3, "to", 2))))
                .isEqualTo("Your hero ran out of hearts and stepped back to level 2, with 3 fresh hearts.");
        assertThat(DigestContent.describe(event(HeroEventType.FAINTED, MON, Map.of())))
                .isEqualTo("Your hero ran out of hearts and fainted, then got back up with 3 fresh hearts.");
        assertThat(DigestContent.describe(lifeLost(1, MON))).isEqualTo("A quiet day cost a heart: 1 heart left.");
        assertThat(DigestContent.describe(lifeLost(2, MON))).isEqualTo("A quiet day cost a heart: 2 hearts left.");
        assertThat(DigestContent.describe(lifeLost(0, MON))).isEqualTo("A quiet day cost the last heart.");
        assertThat(DigestContent.describe(event(HeroEventType.LIFE_GAINED, MON, Map.of("lives", 3, "streak", 7))))
                .isEqualTo("A 7-day streak won back a heart: 3 hearts now.");
        assertThat(DigestContent.describe(event(HeroEventType.RESTED, MON, Map.of())))
                .isEqualTo("A rest day: your hero rested and lost nothing.");
        assertThat(DigestContent.describe(event(HeroEventType.CLASS_CHANGED, MON, pin)))
                .isEqualTo("Your class changed from Rogue to Wizard.");
    }

    @Test
    void splitsHeadlinesFromTheRestInOrder() {
        HeroEvent rested = event(HeroEventType.RESTED, MON, Map.of());
        HeroEvent lost = lifeLost(1, MON.plusDays(1));
        HeroEvent up = levelUp(2, MON.plusDays(2));

        DigestContent content = DigestContent.of(List.of(rested, lost, up));

        assertThat(content.headlines()).extracting(Line::onDate).containsExactly(MON.plusDays(1), MON.plusDays(2));
        assertThat(content.also()).extracting(Line::text).containsExactly("A rest day: your hero rested and lost nothing.");
        assertThat(content.headlines().getFirst().day()).isEqualTo("Tue 27 Oct");
    }

    @Test
    void theSubjectFollowsTheLatestNotableEvent() {
        assertThat(DigestContent.of(List.of(lifeLost(1, MON))).subject()).isEqualTo("Your hero is down to 1 heart");
        assertThat(DigestContent.of(List.of(lifeLost(1, MON), levelUp(3, MON.plusDays(1)))).subject())
                .isEqualTo("Your hero reached level 3");
        assertThat(DigestContent.of(List.of(lifeLost(0, MON),
                event(HeroEventType.LEVEL_DOWN, MON, Map.of("from", 3, "to", 2)))).subject())
                .isEqualTo("Your hero is back at level 2");
        assertThat(DigestContent.of(List.of(event(HeroEventType.FAINTED, MON, Map.of()))).subject())
                .isEqualTo("Your hero fainted and got back up");
    }

    @Test
    void theFallbackIntroFitsTheNews() {
        assertThat(DigestContent.of(List.of(levelUp(2, MON))).fallbackIntro()).isEqualTo(DigestContent.INTRO_GOOD);
        assertThat(DigestContent.of(List.of(lifeLost(1, MON))).fallbackIntro()).isEqualTo(DigestContent.INTRO_SETBACK);
        assertThat(DigestContent.of(List.of(lifeLost(1, MON), levelUp(2, MON.plusDays(1)))).fallbackIntro())
                .isEqualTo(DigestContent.INTRO_MIXED);
    }

    @Test
    void theFallbackIntrosAreKind() {
        // the persona: no guilt trips, rest is healthy
        for (String intro : List.of(DigestContent.INTRO_GOOD, DigestContent.INTRO_SETBACK, DigestContent.INTRO_MIXED)) {
            assertThat(intro.toLowerCase()).doesNotContain("lazy", "fail", "should have", "disappoint");
        }
    }
}
