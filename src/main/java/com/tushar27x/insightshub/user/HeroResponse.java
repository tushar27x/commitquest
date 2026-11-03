package com.tushar27x.insightshub.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tushar27x.insightshub.entity.Archetype;
import com.tushar27x.insightshub.hero.Mood;
import com.tushar27x.insightshub.hero.Tier;
import com.tushar27x.insightshub.hero.WeekStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** {@code GET /api/user/hero} ({@code docs/api/fixtures/hero.json}). Field order follows the fixture. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record HeroResponse(
        int level,
        int xp,
        int xpForNext,
        int lives,
        int maxLives,
        int streak,
        int streakForNextLife,
        Mood mood,
        Mood baseMood,
        Tier tier,
        Today today,
        List<Day> week,
        String timezone,
        List<DayOfWeek> restDays,
        @JsonProperty("class") HeroClass heroClass,
        LocalDate lastEvaluatedOn) {

    /** @param dayEndsAt the next local midnight, for the countdown */
    @Schema(name = "HeroToday")
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Today(LocalDate date, boolean active, int calendarCount, int branchCommitCount, Instant dayEndsAt) {
    }

    @Schema(name = "HeroWeekDay")
    public record Day(LocalDate date, WeekStatus status) {
    }

    /** @param canSwitchAt null when a switch is allowed now */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record HeroClass(@Schema(types = {"string", "null"},
                                    description = "The pinned class; null follows the top score")
                            Archetype chosen,
                            Archetype current, Archetype scored, List<Archetype> qualified,
                            @Schema(types = {"string", "null"}, format = "date-time",
                                    description = "null when a switch is allowed now")
                            Instant canSwitchAt) {
    }
}
