package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;
import com.tushar27x.insightshub.hero.HeroState;
import com.tushar27x.insightshub.notify.DigestIntro.Intro;
import com.tushar27x.insightshub.notify.DigestPlan.UnsentEvent;
import com.tushar27x.insightshub.notify.DigestService.Outcome;
import com.tushar27x.insightshub.repository.DigestRepository;
import com.tushar27x.insightshub.repository.DigestRepository.Candidate;
import com.tushar27x.insightshub.repository.HeroRepository;
import com.tushar27x.insightshub.security.SecurityTestData;
import com.tushar27x.insightshub.security.UnsubscribeTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** One user's digest: due, plan, intro, send, then record; or the skip. */
class DigestServiceTest {

    private static final long ID = 583231L;
    /** 09:00 in Kolkata */
    private static final Instant NOW = Instant.parse("2026-10-29T03:30:00Z");
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 28);
    private static final HeroState HERO = new HeroState(3, 40, 1, 0, YESTERDAY);

    private final DigestRepository digests = mock(DigestRepository.class);
    private final DigestWriter writer = mock(DigestWriter.class);
    private final HeroRepository heroes = mock(HeroRepository.class);
    private final DigestIntro intro = mock(DigestIntro.class);
    private final NotificationChannel channel = mock(NotificationChannel.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final UnsubscribeTokens tokens = new UnsubscribeTokens(SecurityTestData.appProperties(), clock);

    private final DigestService service = new DigestService(digests, writer, heroes, intro, channel, tokens, clock,
            new DigestProperties(true, 8, 2, "Insight Hub <digest@example.test>"), SecurityTestData.appProperties());

    private static Candidate user(boolean alerts, Instant lastSentAt) {
        return new Candidate(ID, "octocat", "octocat@example.test", "Asia/Kolkata", alerts, false, lastSentAt);
    }

    private static UnsentEvent lastHeart(long id) {
        return new UnsentEvent(id, new HeroEvent(HeroEventType.LIFE_LOST, YESTERDAY, Map.of("lives", 1, "reason", "NO_ACTIVITY")));
    }

    private static UnsentEvent rested(long id, LocalDate on) {
        return new UnsentEvent(id, new HeroEvent(HeroEventType.RESTED, on, Map.of()));
    }

    {
        when(channel.name()).thenReturn("EMAIL");
        when(heroes.findState(ID)).thenReturn(Optional.of(HERO));
        when(intro.write(any(), any(), any())).thenReturn(new Intro("You showed up a lot this week.", true));
    }

    @Test
    void sendsThenRecordsTheMailedAndTheStaleEvents() {
        UnsentEvent stale = rested(1, YESTERDAY.minusDays(10));
        when(digests.findUnsent(ID)).thenReturn(List.of(stale, rested(2, YESTERDAY.minusDays(1)), lastHeart(3)));

        assertThat(service.process(user(true, null), NOW).outcome()).isEqualTo(Outcome.SENT);

        var order = inOrder(channel, writer);
        ArgumentCaptor<Digest> sent = ArgumentCaptor.forClass(Digest.class);
        order.verify(channel).send(sent.capture());
        order.verify(writer).record(ID, List.of(2L, 3L), "EMAIL", true, List.of(1L), NOW);

        Digest digest = sent.getValue();
        assertThat(digest.email()).isEqualTo("octocat@example.test");
        assertThat(digest.subject()).isEqualTo("Your hero is down to 1 heart");
        assertThat(digest.intro()).isEqualTo("You showed up a lot this week.");
        assertThat(digest.headlines()).hasSize(1);
        assertThat(digest.also()).hasSize(1);
        assertThat(digest.hero()).isEqualTo(HERO);
        assertThat(digest.appUrl()).isEqualTo("http://localhost:3000/home");
        assertThat(digest.settingsUrl()).isEqualTo("http://localhost:3000/settings");
        assertThat(digest.unsubscribeUrl()).startsWith("http://localhost:8080/api/notifications/unsubscribe?token=");
        String token = digest.unsubscribeUrl().substring(digest.unsubscribeUrl().indexOf("token=") + 6);
        assertThat(tokens.verify(token)).hasValue(ID);
    }

    @Test
    void anAiOutageStillSendsWithTheStaticIntro() {
        when(digests.findUnsent(ID)).thenReturn(List.of(lastHeart(3)));
        when(intro.write(any(), any(), any())).thenReturn(new Intro(DigestContent.INTRO_SETBACK, false));

        var result = service.process(user(true, null), NOW);

        assertThat(result).isEqualTo(new DigestService.Result(Outcome.SENT, true, false));
        verify(channel).send(any());
        verify(writer).record(ID, List.of(3L), "EMAIL", false, List.of(), NOW);
    }

    @Test
    void aFailedSendRecordsNothing() {
        when(digests.findUnsent(ID)).thenReturn(List.of(lastHeart(3)));
        doThrow(new NotificationException("SMTP send failed: MailSendException", null)).when(channel).send(any());

        var result = service.process(user(true, null), NOW);

        assertThat(result).isEqualTo(new DigestService.Result(Outcome.FAILED, true, true));
        verifyNoInteractions(writer);
    }

    @Test
    void aFailedRecordAfterTheSendPropagates() {
        // at-least-once: the email went out, the events stay unsent, and the next run sends again
        when(digests.findUnsent(ID)).thenReturn(List.of(lastHeart(3)));
        doThrow(new IllegalStateException("db down")).when(writer)
                .record(anyLong(), anyList(), any(), anyBoolean(), anyList(), any());

        assertThatThrownBy(() -> service.process(user(true, null), NOW)).isInstanceOf(IllegalStateException.class);
        verify(channel).send(any());
    }

    @Test
    void nothingNotableIsSkippedWithoutAnEmailOrAnAiCall() {
        when(digests.findUnsent(ID)).thenReturn(List.of(rested(4, YESTERDAY)));

        assertThat(service.process(user(true, null), NOW).outcome()).isEqualTo(Outcome.SKIPPED);

        verify(writer).record(ID, List.of(), null, false, List.of(4L), NOW);
        verifyNoInteractions(intro);
        verify(channel, never()).send(any());
    }

    @Test
    void alertsOffSkipsEvenNotableEvents() {
        when(digests.findUnsent(ID)).thenReturn(List.of(lastHeart(3)));

        assertThat(service.process(user(false, null), NOW).outcome()).isEqualTo(Outcome.SKIPPED);

        verify(writer).record(ID, List.of(), null, false, List.of(3L), NOW);
        verify(channel, never()).send(any());
    }

    @Test
    void needsReauthOrNoEmailIsIneligible() {
        assertThat(new Candidate(ID, "o", "o@example.test", "UTC", true, true, null).eligible()).isFalse();
        assertThat(new Candidate(ID, "o", null, "UTC", true, false, null).eligible()).isFalse();
        assertThat(new Candidate(ID, "o", " ", "UTC", true, false, null).eligible()).isFalse();
        assertThat(new Candidate(ID, "o", "o@example.test", "UTC", true, false, null).eligible()).isTrue();
    }

    @Test
    void beforeTheSendHourNothingHappens() {
        Instant sevenInKolkata = Instant.parse("2026-10-29T01:30:00Z");

        assertThat(service.process(user(true, null), sevenInKolkata).outcome()).isEqualTo(Outcome.NOT_DUE);

        verifyNoInteractions(writer, intro, channel);
        verify(digests, never()).findUnsent(anyLong());
    }

    @Test
    void alreadySentTodayWaitsForTomorrow() {
        Instant sentAtEight = Instant.parse("2026-10-29T02:35:00Z");

        assertThat(service.process(user(true, sentAtEight), NOW).outcome()).isEqualTo(Outcome.NOT_DUE);

        verifyNoInteractions(writer, intro, channel);
    }

    @Test
    void eventsStampedMeanwhileAreNotDue() {
        when(digests.findUnsent(ID)).thenReturn(List.of());

        assertThat(service.process(user(true, null), NOW).outcome()).isEqualTo(Outcome.NOT_DUE);

        verifyNoInteractions(writer);
    }

    @Test
    void anUnknownZoneFallsBackToUtc() {
        Candidate odd = new Candidate(ID, "octocat", "octocat@example.test", "Mars/Olympus", true, false, null);
        when(digests.findUnsent(ID)).thenReturn(List.of(lastHeart(3)));

        // 03:30 UTC is before 08:00 UTC
        assertThat(service.process(odd, NOW).outcome()).isEqualTo(Outcome.NOT_DUE);
        assertThat(service.process(odd, Instant.parse("2026-10-29T08:00:00Z")).outcome()).isEqualTo(Outcome.SENT);
        verify(writer).record(eq(ID), eq(List.of(3L)), eq("EMAIL"), eq(true), eq(List.of()), any());
    }
}
