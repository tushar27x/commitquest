package com.tushar27x.insightshub.github.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * GraphQL response envelope. GraphQL reports most failures with HTTP 200 and an {@code errors}
 * array, so callers must check {@link #errors()} themselves.
 */
@JsonIgnoreProperties(ignoreUnknown = true) // e.g. top-level "extensions"
public record GraphQlResponse<T>(T data, List<Error> errors) {

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true) // "locations", "path", "extensions"
    public record Error(String type, String message) {
    }
}
