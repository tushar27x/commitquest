package com.tushar27x.insightshub.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.UniqueElements;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.DayOfWeek;
import java.util.List;

/**
 * {@code GET} and {@code PUT /api/user/preferences} ({@code docs/api/fixtures/preferences.json}). A PUT sends
 * every field. A {@code rest_days} change applies from the next local day.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Preferences(
        @NotNull @IanaTimeZone String timezone,
        @NotNull @Size(max = 2, message = "at most 2 rest days") @UniqueElements(message = "must not repeat a day")
        List<@NotNull DayOfWeek> restDays,
        @NotNull Boolean alertsEnabled,
        @NotNull Boolean publicProfile) {
}
