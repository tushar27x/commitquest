package com.tushar27x.insightshub.github.model;

import java.time.Instant;

/** The {@code data} object of the user-insights query. */
public record UserInsightsData(RateLimit rateLimit, GitHubUser user) {

    /** GitHub GraphQL budget: 5,000 points per hour per token. */
    public record RateLimit(int cost, int remaining, Instant resetAt) {
    }
}
