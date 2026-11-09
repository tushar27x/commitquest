package com.tushar27x.insightshub.github.model;

import java.time.Instant;

/** The {@code data} object of the user-insights query. */
public record UserInsightsData(RateLimit rateLimit, GitHubUser user, String oauthScopes) {

    public UserInsightsData(RateLimit rateLimit, GitHubUser user) {
        this(rateLimit, user, null);
    }

    /**
     * B10: the {@code X-OAuth-Scopes} header of the response, set by the client (not part of GraphQL's JSON);
     * null when GitHub sent none (e.g. a GitHub App token).
     */
    public UserInsightsData withOauthScopes(String scopes) {
        return new UserInsightsData(rateLimit, user, scopes);
    }

    /** GitHub GraphQL budget: 5,000 points per hour per token. */
    public record RateLimit(int cost, int remaining, Instant resetAt) {
    }
}
