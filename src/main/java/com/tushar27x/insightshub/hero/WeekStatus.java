package com.tushar27x.insightshub.hero;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** One cell of the week strip. Lower-case in the API ({@code active}, {@code missed}, ...), unlike the other enums. */
public enum WeekStatus {
    ACTIVE,
    MISSED,
    REST,
    TODAY,
    FUTURE;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }
}
