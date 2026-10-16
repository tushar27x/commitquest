package com.tushar27x.insightshub.hero;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * One row of {@code hero_events}. {@code details} per type (the {@code hero-events.json} fixture shows the first three):
 * <ul>
 *   <li>{@code LIFE_LOST}: {@code {lives, reason: "NO_ACTIVITY"}}, {@code lives} after the loss (0 just before a level-down or faint)</li>
 *   <li>{@code LIFE_GAINED}: {@code {lives, streak}}</li>
 *   <li>{@code LEVEL_UP}, {@code LEVEL_DOWN}: {@code {from, to}}</li>
 *   <li>{@code FAINTED}, {@code RESTED}: {@code {}}</li>
 * </ul>
 */
public record HeroEvent(HeroEventType type, LocalDate onDate, Map<String, Object> details) {

    public HeroEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(onDate, "onDate");
        details = Map.copyOf(details);
    }
}
