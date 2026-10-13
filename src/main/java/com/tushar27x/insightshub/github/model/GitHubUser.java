package com.tushar27x.insightshub.github.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The {@code user} part of graphql/user-insights.graphql, mapped field-for-field.
 * GitHub returns {@code null} for missing objects (empty repo, no README, no default branch),
 * so nested records may be null; interpreting the data is the insights engine's job (Phase 4).
 */
public record GitHubUser(
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
            Tree tree) {
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

    public record PullRequest(int additions, int deletions, String title) {
    }

    public record ContributionsCollection(
            List<RepositoryContributions> commitContributionsByRepository,
            ContributionCalendar contributionCalendar,
            int totalCommitContributions,
            int totalPullRequestContributions,
            int totalIssueContributions,
            int totalRepositoryContributions) {
    }

    public record RepositoryContributions(RepositoryName repository, TotalCount contributions) {
    }

    public record RepositoryName(String name) {
    }

    public record ContributionCalendar(int totalContributions, List<Week> weeks) {
    }

    public record Week(List<ContributionDay> contributionDays) {
    }

    public record ContributionDay(LocalDate date, int contributionCount) {
    }
}
