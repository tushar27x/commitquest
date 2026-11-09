package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.github.model.GitHubUser;
import com.tushar27x.insightshub.github.model.GitHubUser.Commit;
import com.tushar27x.insightshub.github.model.GitHubUser.Connection;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionsCollection;
import com.tushar27x.insightshub.github.model.GitHubUser.PullRequest;
import com.tushar27x.insightshub.github.model.GitHubUser.RepositoryContributions;
import com.tushar27x.insightshub.github.model.GitHubUser.Repository;
import com.tushar27x.insightshub.insights.UserStats.CodeChurn;
import com.tushar27x.insightshub.insights.UserStats.RankedRepo;
import com.tushar27x.insightshub.insights.UserStats.RepoDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns raw GitHub data into {@link UserStats} (a port of v1's {@code calculate_user_insights}),
 * a {@link HabitProfile} and archetype scores. Pure logic with no I/O, so it is unit-tested directly.
 * <p>
 * B10: a private repo (when the user opted in) counts in the aggregates (stars, languages, habit signals, the
 * totals GitHub reports) but is never named: it is left out of {@code ranked_repos}, {@code repo_details} and
 * {@code recent_commits}, so no private name, README, file list or commit message reaches storage or a prompt.
 * <p>
 * Deliberate differences from v1:
 * <ul>
 *   <li>Null objects from GitHub (e.g. an empty repo's {@code defaultBranchRef}) are handled;
 *       v1 crashed on them.</li>
 *   <li>The weekly heatmap is always exactly 7 entries; v1 returned empty lists when the
 *       calendar had no weeks.</li>
 *   <li>Archetypes are scored D&amp;D-style classes ({@link ScoringArchetypeClassifier}), not v1's
 *       first-match rules.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class InsightsEngine {

    static final int TOP_LANGUAGES = 5;
    static final int WEEK_DAYS = 7;
    static final int MONTH_DAYS = 28;
    static final String MISSING_DAY_LABEL = "N/A";

    private final ArchetypeClassifier archetypeClassifier;

    /** Ranked repos stay the top 5 (v1's shape), though the query now lists up to 100 repos for the totals (B11). */
    static final int RANKED_REPOS = 5;

    /** The user's data as fetched: private access, if it was in the query, counts. */
    public InsightsResult calculate(GitHubUser user) {
        return calculate(user, true);
    }

    /**
     * B11: with {@code includePrivate} false the totals are the sums of the per-repo lists with the private entries
     * dropped (GitHub's {@code total*Contributions} have no privacy filter, so a token with the {@code repo} scope
     * would count private work in them), and private pull requests leave the churn and the habit signals.
     */
    public InsightsResult calculate(GitHubUser user, boolean includePrivate) {
        return compute(includePrivate ? user : publicOnly(user));
    }

    /** What the user's public work alone adds up to: see {@link #calculate(GitHubUser, boolean)}. */
    static GitHubUser publicOnly(GitHubUser user) {
        ContributionsCollection c = user.contributionsCollection();
        ContributionsCollection publicContributions = c == null ? null : new ContributionsCollection(
                publicEntries(c.commitContributionsByRepository()),
                c.contributionCalendar(),
                publicTotal(c.commitContributionsByRepository(), c.totalCommitContributions()),
                publicTotal(c.pullRequestContributionsByRepository(), c.totalPullRequestContributions()),
                publicTotal(c.issueContributionsByRepository(), c.totalIssueContributions()),
                c.totalRepositoryContributions(),
                publicEntries(c.pullRequestContributionsByRepository()),
                publicEntries(c.issueContributionsByRepository()));
        return new GitHubUser(user.id(), user.login(), user.name(), user.avatarUrl(), user.url(), user.bio(),
                user.followers(), user.following(),
                user.repositories() == null ? null : new Connection<>(
                        nodes(user.repositories()).stream().filter(repo -> !repo.privateRepo()).toList()),
                user.pullRequests() == null ? null : new Connection<>(
                        nodes(user.pullRequests()).stream().filter(pr -> !pr.privateRepo()).toList()),
                publicContributions);
    }

    private static List<RepositoryContributions> publicEntries(List<RepositoryContributions> byRepo) {
        return byRepo == null ? null : byRepo.stream()
                .filter(rc -> rc.repository() == null || !rc.repository().privateRepo()).toList();
    }

    /** The sum of the public entries; a list that wasn't fetched (null) keeps GitHub's own total. */
    private static int publicTotal(List<RepositoryContributions> byRepo, int total) {
        return byRepo == null ? total : publicEntries(byRepo).stream().mapToInt(rc -> totalCount(rc.contributions())).sum();
    }

    private InsightsResult compute(GitHubUser user) {
        ContributionsCollection contributions = user.contributionsCollection();
        List<Repository> repos = nodes(user.repositories());
        List<ContributionDay> days = sortedDays(contributions);

        List<ContributionDay> lastWeek = lastN(days, WEEK_DAYS);
        List<Integer> weekHeatmap = new ArrayList<>(counts(lastWeek));
        List<String> weekLabels = new ArrayList<>(lastWeek.stream()
                .map(day -> day.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                .toList());
        while (weekHeatmap.size() < WEEK_DAYS) { // pad at the front, oldest side
            weekHeatmap.addFirst(0);
            weekLabels.addFirst(MISSING_DAY_LABEL);
        }

        UserStats stats = new UserStats(
                contributions == null ? 0 : contributions.totalCommitContributions(),
                contributions == null ? 0 : contributions.totalPullRequestContributions(),
                contributions == null ? 0 : contributions.totalIssueContributions(),
                rankedRepos(contributions),
                repos.stream().filter(repo -> !repo.privateRepo()).map(InsightsEngine::repoDetail).toList(),
                recentCommits(repos.stream().filter(repo -> !repo.privateRepo()).toList()),
                codeChurn(nodes(user.pullRequests())),
                topLanguages(repos),
                repos.stream().mapToInt(Repository::stargazerCount).sum(),
                (int) days.stream().filter(day -> day.contributionCount() > 0).count(),
                totalCount(user.followers()) + "/" + totalCount(user.following()),
                List.copyOf(weekHeatmap),
                counts(lastN(days, MONTH_DAYS)),
                List.copyOf(weekLabels));

        HabitProfile habits = HabitProfile.from(user, days);
        return new InsightsResult(stats, habits, archetypeClassifier.classify(habits));
    }

    /** All calendar days with a date, oldest first. */
    public static List<ContributionDay> sortedDays(ContributionsCollection contributions) {
        if (contributions == null || contributions.contributionCalendar() == null
                || contributions.contributionCalendar().weeks() == null) {
            return List.of();
        }
        return contributions.contributionCalendar().weeks().stream()
                .filter(Objects::nonNull)
                .flatMap(week -> Optional.ofNullable(week.contributionDays()).orElse(List.of()).stream())
                .filter(day -> day.date() != null)
                .sorted(Comparator.comparing(ContributionDay::date))
                .toList();
    }

    private static List<RankedRepo> rankedRepos(ContributionsCollection contributions) {
        if (contributions == null || contributions.commitContributionsByRepository() == null) {
            return List.of();
        }
        return contributions.commitContributionsByRepository().stream()
                .filter(rc -> !rc.repository().privateRepo()) // B10: private repos are counted, never listed
                .map(rc -> new RankedRepo(rc.repository().name(), totalCount(rc.contributions())))
                .sorted(Comparator.comparingInt(RankedRepo::count).reversed()) // stable, like v1
                .limit(RANKED_REPOS)
                .toList();
    }

    private static RepoDetail repoDetail(Repository repo) {
        String readme = repo.readme() == null || repo.readme().text() == null ? "" : repo.readme().text();
        List<String> files = repo.tree() == null || repo.tree().entries() == null
                ? List.of()
                : repo.tree().entries().stream().map(GitHubUser.TreeEntry::name).toList();
        return new RepoDetail(repo.name(), readme, files);
    }

    /** Commit messages from each repo's default-branch history, in repo order. */
    private static List<String> recentCommits(List<Repository> repos) {
        return repos.stream()
                .flatMap(repo -> history(repo).stream())
                .map(Commit::message)
                .filter(message -> message != null && !message.isEmpty())
                .toList();
    }

    static List<Commit> history(Repository repo) {
        if (repo.defaultBranchRef() == null || repo.defaultBranchRef().target() == null) {
            return List.of(); // empty repo, or the ref doesn't point at a commit
        }
        return nodes(repo.defaultBranchRef().target().history());
    }

    private static CodeChurn codeChurn(List<PullRequest> pullRequests) {
        return new CodeChurn(
                pullRequests.stream().mapToInt(PullRequest::additions).sum(),
                pullRequests.stream().mapToInt(PullRequest::deletions).sum());
    }

    /** Languages ranked by how many top repos use them; ties keep first-seen order (as v1). */
    private static List<String> topLanguages(List<Repository> repos) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        repos.stream()
                .flatMap(repo -> nodes(repo.languages()).stream())
                .map(GitHubUser.Language::name)
                .filter(Objects::nonNull)
                .forEach(name -> counts.merge(name, 1, Integer::sum));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_LANGUAGES)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static <T> List<T> lastN(List<T> list, int n) {
        return list.subList(Math.max(0, list.size() - n), list.size());
    }

    private static List<Integer> counts(List<ContributionDay> days) {
        return days.stream().map(ContributionDay::contributionCount).toList();
    }

    private static int totalCount(GitHubUser.TotalCount count) {
        return count == null ? 0 : count.totalCount();
    }

    static <T> List<T> nodes(Connection<T> connection) {
        return connection == null || connection.nodes() == null
                ? Collections.emptyList()
                : connection.nodes().stream().filter(Objects::nonNull).toList();
    }
}
