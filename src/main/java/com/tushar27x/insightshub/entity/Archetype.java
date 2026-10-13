package com.tushar27x.insightshub.entity;

/**
 * Fantasy (D&D-style) classes, each tied to one coding habit. Stored by name: adding, removing or
 * renaming a constant needs a migration for the {@code insights_archetype_check} constraint.
 * <p>
 * Declaration order is the tie-break order when two classes score the same.
 */
public enum Archetype {
    PALADIN("paladin", "🛡️ The Paladin", "keeps an oath of daily commits: consistent, unbroken streaks"),
    BARBARIAN("barbarian", "🪓 The Barbarian", "charges in with huge pull requests and bursts of activity"),
    WIZARD("wizard", "🧙 The Wizard", "masters many languages across their projects"),
    ARTIFICER("artificer", "⚙️ The Artificer", "builds the machinery: Docker, CI, infrastructure as code"),
    CLERIC("cleric", "✨ The Cleric", "heals codebases: reports issues and lands fixes"),
    BARD("bard", "🎻 The Bard", "works through pull requests, documents well and draws an audience"),
    MONK("monk", "🧘 The Monk", "disciplined: small pull requests and descriptive commit messages"),
    ROGUE("rogue", "🗡️ The Rogue", "strikes from the shadows, doing much of their work on weekends");

    private final String slug;
    private final String label;
    private final String habit;

    Archetype(String slug, String label, String habit) {
        this.slug = slug;
        this.label = label;
        this.habit = habit;
    }

    public String getSlug() {
        return slug;
    }

    public String getLabel() {
        return label;
    }

    /** The coding habit this class stands for; shown to users and fed to the AI prompts. */
    public String getHabit() {
        return habit;
    }
}
