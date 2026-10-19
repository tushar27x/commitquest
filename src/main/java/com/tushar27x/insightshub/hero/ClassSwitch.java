package com.tushar27x.insightshub.hero;

import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.hero.HeroView.ClassView;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code PUT /api/user/hero/class} (plan §2 "Class switching", contract.md) as one pure function like
 * {@link HeroRules}: no clock, no database. Checked in this order:
 * <ol>
 *   <li>the stored choice again ({@code requested == chosen}): {@link Unchanged}, even in the cooldown;</li>
 *   <li>a class that scores below the threshold and isn't the top class: {@link NotQualified} (waiting wouldn't
 *       help, so it comes before the cooldown). {@code null}, following the top score, always qualifies;</li>
 *   <li>within the cooldown of the last change: {@link CoolingDown};</li>
 *   <li>otherwise a {@link Change}. Pinning the class already followed ({@code null → scored}) is a change too:
 *       the stored value changes, so it starts the cooldown, with {@code from == to} in the event.</li>
 * </ol>
 * Level, XP, lives and streak never change.
 */
public final class ClassSwitch {

    private ClassSwitch() {
    }

    /**
     * @param scores    0–100 per class from the latest insights; a missing class counts as 0
     * @param scored    the top-scored class ({@code insights.archetype})
     * @param chosen    {@code users.chosen_archetype}; null follows {@code scored}
     * @param changedAt {@code users.archetype_changed_at}; null if never changed
     * @param requested the new choice; null = follow the top score
     * @param today     the user's local date now: the event's {@code on_date}
     */
    public static Decision decide(Map<Archetype, Integer> scores, Archetype scored, Archetype chosen,
                                  Instant changedAt, Archetype requested, Instant now, LocalDate today,
                                  int qualifyScore, Duration cooldown) {
        if (requested == chosen) {
            return new Unchanged();
        }
        ClassView view = HeroView.classView(scores, scored, chosen, changedAt, now, qualifyScore, cooldown);
        if (requested != null && !view.qualified().contains(requested)) {
            return new NotQualified(requested, scores.getOrDefault(requested, 0));
        }
        if (view.canSwitchAt() != null) {
            return new CoolingDown(view.canSwitchAt());
        }
        Map<String, Object> details = new HashMap<>();
        details.put("from", view.current().name());
        details.put("to", (requested == null ? scored : requested).name());
        details.put("chosen", requested == null ? null : requested.name());
        return new Change(requested, new HeroEvent(HeroEventType.CLASS_CHANGED, today, details));
    }

    public sealed interface Decision permits Unchanged, NotQualified, CoolingDown, Change {
    }

    /** The request is what's already stored: nothing to write, no event, the cooldown isn't reset. */
    public record Unchanged() implements Decision {
    }

    /** → 409. */
    public record NotQualified(Archetype requested, int score) implements Decision {
    }

    /** → 429 with {@code Retry-After}. */
    public record CoolingDown(Instant canSwitchAt) implements Decision {

        public Duration retryAfter(Instant now) {
            return Duration.between(now, canSwitchAt);
        }
    }

    /** Store {@code chosen} (null = follow the top score), start the cooldown, and add {@code event}. */
    public record Change(Archetype chosen, HeroEvent event) implements Decision {
    }
}
