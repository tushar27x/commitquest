package com.tushar27x.insightshub.github.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The {@code user} part of graphql/user-insights.graphql, mapped field-for-field.
 * GitHub returns {@code null} for missing objects (empty repo, no README, no default branch),
 * so nested records may be null; interpreting the data is the insights engine's job (Phase 4).
 * {@code id} is the GraphQL node id, the branch scan's {@code author} filter.
 */
public record GitHubUser(
        String id,
        String login,
        String name,
        String avatarUrl,
        String url,
        String bio,
        TotalCount followers,
        TotalCount following,
        Connection<Repository> repositories,
        Connection<PullRequest> pullRequests,
        ContributionsCollection contributionsCollection) {

    /** GraphQL connection: only {@code nodes} is requested. */
    public record Connection<T>(List<T> nodes) {
    }

    public record TotalCount(int totalCount) {
    }

    public record Repository(
            String name,
            int stargazerCount,
            Connection<Language> languages,
            Ref defaultBranchRef,
            Blob readme,
            Tree tree,
            Boolean isPrivate) {

        /** B10: absent counts as public (only a repo GitHub says is private is hidden). */
        public boolean privateRepo() {
            return Boolean.TRUE.equals(isPrivate);
        }

        public Repository(String name, int stargazerCount, Connection<Language> languages, Ref defaultBranchRef,
                          Blob readme, Tree tree) {
            this(name, stargazerCount, languages, defaultBranchRef, readme, tree, false);
        }
    }

    public record Language(String name) {
    }

    public record Ref(CommitTarget target) {
    }

    /** {@code history} is null when the ref points at something other than a commit. */
    public record CommitTarget(Connection<Commit> history) {
    }

    public record Commit(String oid, String message, Instant committedDate, Author author) {
    }

    /** {@code user} is null when the commit email isn't linked to a GitHub account. */
    public record Author(UserRef user) {
    }

    public record UserRef(String login) {
    }

    public record Blob(String text) {
    }

    public record Tree(List<TreeEntry> entries) {
    }

    public record TreeEntry(String name, String type) {
    }

    /** {@code repository} (B11) carries only {@code isPrivate}: a private PR is dropped from the churn when OFF. */
    public record PullRequest(int additions, int deletions, String title, RepositoryName repository) {

        public PullRequest(int additions, int deletions, String title) {
            this(additions, deletions, title, null);
        }

        public boolean privateRepo() {
            return repository != null && repository.privateRepo();
        }
    }

    public record ContributionsCollection(
            List<RepositoryContributions> commitContributionsByRepository,
            ContributionCalendar contributionCalendar,
            int totalCommitContributions,
            int totalPullRequestContributions,
            int totalIssueContributions,
            int totalRepositoryContributions,
            List<RepositoryContributions> pullRequestContributionsByRepository,
            List<RepositoryContributions> issueContributionsByRepository) {

        /** Without the per-repo PR and issue lists (null = not fetched): the totals fall back to GitHub's own. */
        public ContributionsCollection(List<RepositoryContributions> commitContributionsByRepository,
                                       ContributionCalendar contributionCalendar, int totalCommitContributions,
                                       int totalPullRequestContributions, int totalIssueContributions,
                                       int totalRepositoryContributions) {
            this(commitContributionsByRepository, contributionCalendar, totalCommitContributions,
                    totalPullRequestContributions, totalIssueContributions, totalRepositoryContributions, null, null);
        }
    }

    public record RepositoryContributions(RepositoryName repository, TotalCount contributions) {
    }

    public record RepositoryName(String name, Boolean isPrivate) {

        public boolean privateRepo() {
            return Boolean.TRUE.equals(isPrivate);
        }

        public RepositoryName(String name) {
            this(name, false);
        }
    }

    public record ContributionCalendar(int totalContributions, List<Week> weeks) {
    }

    public record Week(List<ContributionDay> contributionDays) {
    }

    public record ContributionDay(LocalDate date, int contributionCount) {
    }
}
