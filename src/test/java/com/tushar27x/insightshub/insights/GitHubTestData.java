package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.github.model.GitHubUser;
import com.tushar27x.insightshub.github.model.GitHubUser.Author;
import com.tushar27x.insightshub.github.model.GitHubUser.Blob;
import com.tushar27x.insightshub.github.model.GitHubUser.Commit;
import com.tushar27x.insightshub.github.model.GitHubUser.CommitTarget;
import com.tushar27x.insightshub.github.model.GitHubUser.Connection;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionCalendar;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionsCollection;
import com.tushar27x.insightshub.github.model.GitHubUser.Language;
import com.tushar27x.insightshub.github.model.GitHubUser.PullRequest;
import com.tushar27x.insightshub.github.model.GitHubUser.Ref;
import com.tushar27x.insightshub.github.model.GitHubUser.Repository;
import com.tushar27x.insightshub.github.model.GitHubUser.RepositoryContributions;
import com.tushar27x.insightshub.github.model.GitHubUser.RepositoryName;
import com.tushar27x.insightshub.github.model.GitHubUser.TotalCount;
import com.tushar27x.insightshub.github.model.GitHubUser.Tree;
import com.tushar27x.insightshub.github.model.GitHubUser.TreeEntry;
import com.tushar27x.insightshub.github.model.GitHubUser.UserRef;
import com.tushar27x.insightshub.github.model.GitHubUser.Week;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/** Small factories for GitHub records, so tests only spell out what they care about. */
final class GitHubTestData {

    private GitHubTestData() {
    }

    static GitHubUser user(List<Repository> repos, List<PullRequest> prs, ContributionsCollection contributions) {
        return new GitHubUser("octocat", "The Octocat", null, null, null,
                new TotalCount(10), new TotalCount(2),
                new Connection<>(repos), new Connection<>(prs), contributions);
    }

    static GitHubUser user(ContributionsCollection contributions) {
        return user(List.of(), List.of(), contributions);
    }

    static Repository repo(String name, int stars, List<String> languages, List<String> files,
                           List<String> commitMessages) {
        List<Commit> commits = commitMessages.stream()
                .map(message -> new Commit("sha-" + message, message, Instant.EPOCH, null))
                .toList();
        return new Repository(name, stars,
                new Connection<>(languages.stream().map(Language::new).toList()),
                new Ref(new CommitTarget(new Connection<>(commits))),
                new Blob("# " + name),
                new Tree(files.stream().map(file -> new TreeEntry(file, "blob")).toList()));
    }

    /** A repository with full control over README text and commits (with authors). */
    static Repository repo(String name, String readme, List<String> files, List<Commit> commits) {
        return new Repository(name, 0, new Connection<>(List.of()),
                new Ref(new CommitTarget(new Connection<>(commits))),
                new Blob(readme),
                new Tree(files.stream().map(file -> new TreeEntry(file, "blob")).toList()));
    }

    /** A commit by the GitHub account {@code login}; null login = email not linked to an account. */
    static Commit commit(String message, String login) {
        return new Commit("sha-" + message, message, Instant.EPOCH, new Author(login == null ? null : new UserRef(login)));
    }

    static Repository repoWithLanguages(String name, String... languages) {
        return repo(name, 0, List.of(languages), List.of(), List.of());
    }

    /** A repository with no commits: GitHub returns null for its ref, README and tree. */
    static Repository emptyRepo(String name) {
        return new Repository(name, 0, new Connection<>(List.of()), null, null, null);
    }

    static ContributionsCollection contributions(int commits, int prs, int issues, List<ContributionDay> days) {
        return contributions(commits, prs, issues, days, List.of());
    }

    static ContributionsCollection contributions(int commits, int prs, int issues, List<ContributionDay> days,
                                                 List<RepositoryContributions> byRepo) {
        int total = days.stream().mapToInt(ContributionDay::contributionCount).sum();
        return new ContributionsCollection(byRepo,
                new ContributionCalendar(total, List.of(new Week(days))),
                commits, prs, issues, 0);
    }

    static RepositoryContributions repoContributions(String name, int count) {
        return new RepositoryContributions(new RepositoryName(name), new TotalCount(count));
    }

    static ContributionDay day(String date, int count) {
        return new ContributionDay(LocalDate.parse(date), count);
    }

    /** {@code n} consecutive days starting at {@code start}, with counts 1..n. */
    static List<ContributionDay> days(String start, int n) {
        LocalDate first = LocalDate.parse(start);
        return IntStream.range(0, n)
                .mapToObj(i -> new ContributionDay(first.plusDays(i), i + 1))
                .toList();
    }
}
