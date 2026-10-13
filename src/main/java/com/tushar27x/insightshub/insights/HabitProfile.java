package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.github.model.GitHubUser;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionDay;
import com.tushar27x.insightshub.github.model.GitHubUser.ContributionsCollection;
import com.tushar27x.insightshub.github.model.GitHubUser.PullRequest;
import com.tushar27x.insightshub.github.model.GitHubUser.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Coding-habit signals derived from one GitHub snapshot; the input to archetype scoring.
 * Shares are 0..1, and a share over an empty sample is 0.
 * <p>
 * The commit and PR samples are small (the GraphQL query fetches 5 commits from each of the top 5 repos,
 * and the last 10 PRs), so those signals are rough. Only the user's own commits count: a repo's
 * history also holds commits by other people.
 *
 * @param activeDayRatio         days with a contribution / calendar days
 * @param longestStreak          most consecutive days with a contribution
 * @param weekendShare           Saturday + Sunday contributions / all contributions
 * @param burstiness             busiest day / average active day (1 = perfectly even)
 * @param languageCount          distinct languages across the top repos
 * @param infraRepoShare         top repos with infrastructure files at the root
 * @param issueShare             issues / (commits + PRs + issues)
 * @param prShare                PRs / (commits + PRs)
 * @param audience               followers + stars on the top repos
 * @param documentedRepoShare    top repos with a README of at least {@value #DOCUMENTED_README_CHARS} characters
 * @param prCount                PRs in the sample
 * @param avgPrSize              average lines added + deleted per PR in the sample
 * @param ownCommitCount         the user's own commits in the sample
 * @param fixCommitShare         own commits whose subject mentions a fix
 * @param descriptiveCommitShare own commits with a descriptive subject
 */
public record HabitProfile(
        double activeDayRatio,
        int longestStreak,
        double weekendShare,
        double burstiness,
        int languageCount,
        double infraRepoShare,
        double issueShare,
        double prShare,
        int audience,
        double documentedRepoShare,
        int prCount,
        double avgPrSize,
        int ownCommitCount,
        double fixCommitShare,
        double descriptiveCommitShare) {

    static final int DOCUMENTED_README_CHARS = 500;

    /** Root entries (lowercased) that mean infrastructure work. Exact names, unlike v1's substrings. */
    static final Set<String> INFRA_ENTRIES = Set.of(
            "dockerfile", "docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml",
            ".gitlab-ci.yml", "jenkinsfile", "vagrantfile", "procfile", "fly.toml", "serverless.yml",
            "skaffold.yaml", "terraform", "k8s", "kubernetes", "helm", "charts", "ansible", "infra", "deploy");

    static final Pattern FIX_SUBJECT = Pattern.compile("\\b(fix(es|ed)?|bug(fix)?|hotfix|patch|resolves?)\\b");

    static final int DESCRIPTIVE_MIN_WORDS = 3;
    static final int DESCRIPTIVE_MIN_CHARS = 15;

    public static HabitProfile from(GitHubUser user, List<ContributionDay> sortedDays) {
        ContributionsCollection contributions = user.contributionsCollection();
        int commits = contributions == null ? 0 : contributions.totalCommitContributions();
        int prs = contributions == null ? 0 : contributions.totalPullRequestContributions();
        int issues = contributions == null ? 0 : contributions.totalIssueContributions();

        List<Repository> repos = InsightsEngine.nodes(user.repositories());
        List<PullRequest> pullRequests = InsightsEngine.nodes(user.pullRequests());
        List<String> ownSubjects = ownCommitSubjects(user.login(), repos);

        int total = sortedDays.stream().mapToInt(ContributionDay::contributionCount).sum();
        int weekend = sortedDays.stream().filter(day -> isWeekend(day.date()))
                .mapToInt(ContributionDay::contributionCount).sum();
        long activeDays = sortedDays.stream().filter(day -> day.contributionCount() > 0).count();
        int stars = repos.stream().mapToInt(Repository::stargazerCount).sum();
        int followers = user.followers() == null ? 0 : user.followers().totalCount();
        int churn = pullRequests.stream().mapToInt(pr -> pr.additions() + pr.deletions()).sum();

        return new HabitProfile(
                share(activeDays, sortedDays.size()),
                longestStreak(sortedDays),
                share(weekend, total),
                burstiness(sortedDays),
                languageCount(repos),
                share(repos.stream().filter(HabitProfile::hasInfraFiles).count(), repos.size()),
                share(issues, commits + prs + issues),
                share(prs, commits + prs),
                followers + stars,
                share(repos.stream().filter(HabitProfile::isDocumented).count(), repos.size()),
                pullRequests.size(),
                share(churn, pullRequests.size()),
                ownSubjects.size(),
                share(ownSubjects.stream().filter(HabitProfile::isFix).count(), ownSubjects.size()),
                share(ownSubjects.stream().filter(HabitProfile::isDescriptive).count(), ownSubjects.size()));
    }

    static int longestStreak(List<ContributionDay> sortedDays) {
        int longest = 0;
        int current = 0;
        LocalDate previous = null;
        for (ContributionDay day : sortedDays) {
            if (day.contributionCount() <= 0) {
                current = 0;
            } else if (previous != null && current > 0 && day.date().equals(previous.plusDays(1))) {
                current++;
            } else {
                current = 1; // first active day, or a gap in the calendar
            }
            longest = Math.max(longest, current);
            previous = day.date();
        }
        return longest;
    }

    static double burstiness(List<ContributionDay> days) {
        int[] active = days.stream().mapToInt(ContributionDay::contributionCount).filter(count -> count > 0).toArray();
        if (active.length == 0) {
            return 0;
        }
        double mean = (double) Arrays.stream(active).sum() / active.length;
        return Arrays.stream(active).max().orElseThrow() / mean;
    }

    static boolean isFix(String subject) {
        return FIX_SUBJECT.matcher(subject.toLowerCase(Locale.ROOT)).find();
    }

    static boolean isDescriptive(String subject) {
        return subject.length() >= DESCRIPTIVE_MIN_CHARS && subject.split("\\s+").length >= DESCRIPTIVE_MIN_WORDS;
    }

    static boolean hasInfraFiles(Repository repo) {
        if (repo.tree() == null || repo.tree().entries() == null) {
            return false;
        }
        return repo.tree().entries().stream()
                .map(GitHubUser.TreeEntry::name)
                .filter(Objects::nonNull)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(name -> INFRA_ENTRIES.contains(name) || name.endsWith(".tf") || name.startsWith("dockerfile"));
    }

    private static boolean isDocumented(Repository repo) {
        return repo.readme() != null && repo.readme().text() != null
                && repo.readme().text().length() >= DOCUMENTED_README_CHARS;
    }

    private static int languageCount(List<Repository> repos) {
        return (int) repos.stream()
                .flatMap(repo -> InsightsEngine.nodes(repo.languages()).stream())
                .map(GitHubUser.Language::name)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    /** First lines of the commits authored by {@code login}, skipping blank ones. */
    private static List<String> ownCommitSubjects(String login, List<Repository> repos) {
        if (login == null) {
            return List.of();
        }
        return repos.stream()
                .flatMap(repo -> InsightsEngine.history(repo).stream())
                .filter(commit -> commit.author() != null && commit.author().user() != null
                        && login.equalsIgnoreCase(commit.author().user().login()))
                .map(GitHubUser.Commit::message)
                .filter(Objects::nonNull)
                .map(message -> message.lines().findFirst().orElse("").strip())
                .filter(subject -> !subject.isEmpty())
                .toList();
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private static double share(long part, long whole) {
        return whole == 0 ? 0 : (double) part / whole;
    }
}
