package com.tushar27x.insightshub.github;

/**
 * GitHub rejected the token (HTTP 401): revoked, expired or invalid.
 * Separate from {@link GitHubApiException} so the scheduled sync can mark the user
 * {@code needs_reauth} instead of retrying.
 */
public class GitHubAuthException extends GitHubApiException {

    public GitHubAuthException(String message) {
        super(message);
    }
}
