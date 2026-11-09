package com.tushar27x.insightshub.entity;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** OAuth scope lists: {@code scope} in the token response and {@code X-OAuth-Scopes} (comma-separated, spaces optional). */
public final class GitHubScopes {

    public static final String REPO = "repo";

    private GitHubScopes() {
    }

    public static Set<String> parse(String scopes) {
        if (scopes == null) {
            return Set.of();
        }
        return Arrays.stream(scopes.split("[,\\s]+")).filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static boolean hasRepo(String scopes) {
        return parse(scopes).contains(REPO);
    }

    /** The stored form: trimmed, comma-separated. */
    public static String normalize(String scopes) {
        return String.join(",", parse(scopes));
    }

    public static boolean sameSet(String a, String b) {
        return parse(a).equals(parse(b));
    }
}
