package com.tushar27x.insightshub.github.model;

import com.tushar27x.insightshub.github.model.GitHubUser.Connection;
import com.tushar27x.insightshub.github.model.GitHubUser.TotalCount;
import com.tushar27x.insightshub.github.model.UserInsightsData.RateLimit;

import java.time.Instant;

/** The {@code data} object of graphql/branch-activity.graphql. */
public record BranchActivityData(RateLimit rateLimit, Viewer viewer) {

    public record Viewer(Connection<Repository> repositories) {
    }

    /** {@code pushedAt} is null for a repo that was never pushed to. */
    public record Repository(Instant pushedAt, Connection<Ref> refs) {
    }

    public record Ref(Target target) {
    }

    /** {@code history} is null when the branch points at something other than a commit. */
    public record Target(TotalCount history) {
    }
}
