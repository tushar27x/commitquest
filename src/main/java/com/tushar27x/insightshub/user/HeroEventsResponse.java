package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.hero.HeroEventType;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** {@code GET /api/user/hero/events} ({@code docs/api/fixtures/hero-events.json}), both lists oldest first. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record HeroEventsResponse(List<Event> events, List<XpDay> xpDays) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Event(HeroEventType type, LocalDate onDate, Map<String, Object> details) {
    }

    /** @param xp what the day gave (10 or 0); {@code streak} is the streak after it */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record XpDay(LocalDate onDate, int xp, int streak) {
    }
}
