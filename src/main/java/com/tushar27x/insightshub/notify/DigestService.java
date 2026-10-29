package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.config.InsightsHubProperties;
import com.tushar27x.insightshub.hero.HeroService;
import com.tushar27x.insightshub.hero.HeroState;
import com.tushar27x.insightshub.notify.DigestIntro.Intro;
import com.tushar27x.insightshub.notify.DigestPlan.Plan;
import com.tushar27x.insightshub.notify.DigestPlan.UnsentEvent;
import com.tushar27x.insightshub.repository.DigestRepository;
import com.tushar27x.insightshub.repository.DigestRepository.Candidate;
import com.tushar27x.insightshub.repository.HeroRepository;
import com.tushar27x.insightshub.security.UnsubscribeTokens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * One user's digest for one run: due? → plan → (intro → send → record) or record the skip.
 * <p>
 * <b>At-least-once</b> (plan §12, hint 3): the email is sent first and the events are stamped after, in one
 * transaction. If the send fails nothing is written, and the next hourly run retries. If the stamp fails after a
 * send, the next run sends the same digest again. A repeated email is the lesser evil: the other order
 * (stamp, then send) silently loses the news when the send fails, and the news is the point (a hero at 1 heart).
 */
@Slf4j
@Service
public class DigestService {

    static final String UNSUBSCRIBE_PATH = "/api/notifications/unsubscribe";

    private final DigestRepository digests;
    private final DigestWriter writer;
    private final HeroRepository heroes;
    private final DigestIntro intro;
    private final NotificationChannel channel;
    private final UnsubscribeTokens tokens;
    private final Clock clock;
    private final LocalTime sendAt;
    private final String backendUrl;
    private final String frontendUrl;

    public DigestService(DigestRepository digests, DigestWriter writer, HeroRepository heroes, DigestIntro intro,
                         NotificationChannel channel, UnsubscribeTokens tokens, Clock clock,
                         DigestProperties digestProperties, InsightsHubProperties appProperties) {
        this.digests = digests;
        this.writer = writer;
        this.heroes = heroes;
        this.intro = intro;
        this.channel = channel;
        this.tokens = tokens;
        this.clock = clock;
        this.sendAt = digestProperties.sendAt();
        this.backendUrl = withoutTrailingSlash(appProperties.backendUrl());
        this.frontendUrl = withoutTrailingSlash(appProperties.frontendUrl());
    }

    public List<Candidate> candidates() {
        return digests.findCandidates();
    }

    /**
     * @param now the run's start: decides who is due, so one run treats every user alike
     * @throws RuntimeException on a database error (nothing sent or the send already happened; see the class doc)
     */
    public Result process(Candidate user, Instant now) {
        ZoneId zone = HeroService.zoneOf(user.timezone());
        if (!DigestPlan.isDue(now, zone, sendAt, user.lastSentAt())) {
            return Result.NOT_DUE;
        }
        Plan plan = DigestPlan.plan(digests.findUnsent(user.userId()), DigestPlan.localToday(now, zone),
                user.eligible());
        if (!plan.sends()) {
            if (plan.skip().isEmpty()) {
                return Result.NOT_DUE; // stamped by another run in between
            }
            writer.record(user.userId(), List.of(), null, false, ids(plan.skip()), clock.instant());
            return Result.SKIPPED;
        }

        DigestContent content = DigestContent.of(plan.mail().stream().map(UnsentEvent::event).toList());
        HeroState hero = heroes.findState(user.userId()).orElse(null);
        Intro written = intro.write(user.login(), content, hero);
        Digest digest = new Digest(user.userId(), user.login(), user.email(), content.subject(), written.text(),
                content.headlines(), content.also(), hero,
                backendUrl + UNSUBSCRIBE_PATH + "?token=" + tokens.issue(user.userId()),
                frontendUrl + "/home", frontendUrl + "/settings");
        try {
            channel.send(digest);
        } catch (NotificationException e) {
            log.warn("Digest for user {} not delivered, retried next run: {}", user.userId(), e.getMessage());
            return new Result(Outcome.FAILED, true, written.fromAi());
        }
        writer.record(user.userId(), ids(plan.mail()), channel.name(), written.fromAi(), ids(plan.skip()),
                clock.instant());
        log.info("Digest sent to user {}: {} events ({} headlines), {} intro", user.userId(), plan.mail().size(),
                content.headlines().size(), written.fromAi() ? "AI" : "static");
        return new Result(Outcome.SENT, true, written.fromAi());
    }

    private static List<Long> ids(List<UnsentEvent> events) {
        return events.stream().map(UnsentEvent::id).toList();
    }

    private static String withoutTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public enum Outcome {
        NOT_DUE, SKIPPED, SENT, FAILED
    }

    /**
     * @param introAsked whether the LLM was asked for an intro (it was, for every send attempt)
     * @param introFromAi whether its answer was used (false: the static intro)
     */
    public record Result(Outcome outcome, boolean introAsked, boolean introFromAi) {
        static final Result NOT_DUE = new Result(Outcome.NOT_DUE, false, false);
        static final Result SKIPPED = new Result(Outcome.SKIPPED, false, false);
    }
}
