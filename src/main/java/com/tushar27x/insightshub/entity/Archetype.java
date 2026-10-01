package com.tushar27x.insightshub.entity;

public enum Archetype {
    BUG_HUNTER("bug-hunter", "🐛 The Bug Hunter"),
    OPEN_SOURCE_HERO("open-source-hero", "🦸 The Open Source Hero"),
    SOFTWARE_ARCHITECT("software-architect", "🏗️ The Software Architect"),
    WEEKEND_WARRIOR("weekend-warrior", "⚔️ The Weekend Warrior"),
    CODE_CRUSADER("code-crusader", "🛡️ The Code Crusader");

    private final String slug;
    private final String label;

    Archetype(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    public String getSlug() {
        return slug;
    }

    public String getLabel() {
        return label;
    }
}
