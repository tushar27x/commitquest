package com.tushar27x.insightshub.github;

/**
 * B10: the token is alive but GitHub refused the private part of a query (a FORBIDDEN GraphQL error: the repo
 * scope was removed, or an organization withdrew access). Not a 401, so not needs_reauth: the sync records the
 * lost scope and goes on with public data.
 */
public class GitHubScopeException extends GitHubApiException {

    public GitHubScopeException(String message) {
        super(message);
    }
}
