package com.tushar27x.insightshub.hero;

/**
 * What happened to the hero on a day, stored by name in {@code hero_events.type}: adding a constant
 * needs a migration for {@code hero_events_type_check}. {@code CLASS_CHANGED} comes from the class
 * switch (B6), the others from {@link HeroRules}.
 */
public enum HeroEventType {
    LIFE_LOST,
    LIFE_GAINED,
    LEVEL_UP,
    LEVEL_DOWN,
    FAINTED,
    RESTED,
    CLASS_CHANGED
}
