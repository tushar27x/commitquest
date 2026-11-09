package com.tushar27x.insightshub.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.UniqueElements;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET} and {@code PUT /api/user/preferences} ({@code docs/api/fixtures/preferences.json}). A PUT sends
 * every field but {@code private_access}. A {@code rest_days} change applies from the next local day.
 *
 * @param includePrivate B10: the user's choice to let insights read their private repos; default false
 * @param privateAccess  B10: derived from the scopes recorded at the last login; read-only, ignored on PUT
 * @param privateLockedUntil B11: private_changed_at + the cooldown while that is in the future, else null; read-only
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Preferences(
        @NotNull @IanaTimeZone String timezone,
        @NotNull @Size(max = 2, message = "at most 2 rest days") @UniqueElements(message = "must not repeat a day")
        List<@NotNull DayOfWeek> restDays,
        @NotNull Boolean alertsEnabled,
        @NotNull Boolean publicProfile,
        @NotNull Boolean includePrivate,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) PrivateAccess privateAccess,
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        @Schema(accessMode = Schema.AccessMode.READ_ONLY, nullable = true,
                description = "B11: the end of the 7-day lock on include_private, or null. Read-only, ignored on PUT.")
        Instant privateLockedUntil) {

    /** A request body (or a test's): the read-only lock end is absent. */
    public Preferences(String timezone, List<DayOfWeek> restDays, Boolean alertsEnabled, Boolean publicProfile,
                       Boolean includePrivate, PrivateAccess privateAccess) {
        this(timezone, restDays, alertsEnabled, publicProfile, includePrivate, privateAccess, null);
    }

    /** {@code none | granted} on the wire. */
    public enum PrivateAccess {
        NONE, GRANTED;

        @JsonValue
        public String json() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
