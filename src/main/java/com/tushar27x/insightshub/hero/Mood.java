package com.tushar27x.insightshub.hero;

/**
 * How the hero looks right now (plan §2 "Mood"). Derived on every {@code GET /hero}, never stored.
 * {@code HAPPY}, {@code OKAY} and {@code WEAK} are the base moods (from lives only); the others are overlays.
 */
public enum Mood {
    HAPPY,
    OKAY,
    WEAK,
    HUNGRY,
    SLEEPING,
    LEVEL_UP,
    FAINTED
}
