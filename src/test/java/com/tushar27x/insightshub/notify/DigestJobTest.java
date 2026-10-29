package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.notify.DigestJob.Summary;
import com.tushar27x.insightshub.notify.DigestService.Outcome;
import com.tushar27x.insightshub.notify.DigestService.Result;
import com.tushar27x.insightshub.repository.DigestRepository.Candidate;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The hourly digest job: every candidate on their own, outcomes and LLM calls counted. */
class DigestJobTest {

    private static final Instant NOW = Instant.parse("2026-10-29T08:35:00Z");

    private final DigestService service = mock(DigestService.class);

    private DigestJob job(boolean enabled) {
        return new DigestJob(service, Clock.fixed(NOW, ZoneOffset.UTC),
                new DigestProperties(enabled, 8, 2, enabled ? "digest@example.test" : ""), "smtp.example.test");
    }

    private static List<Candidate> candidates(long... ids) {
        return LongStream.of(ids)
                .mapToObj(id -> new Candidate(id, "user" + id, "u" + id + "@example.test", "UTC", true, false, null))
                .toList();
    }

    @Test
    void countsOutcomesAndLlmCallsAndIsolatesFailures() {
        List<Candidate> users = candidates(1, 2, 3, 4, 5, 6);
        when(service.candidates()).thenReturn(users);
        when(service.process(users.get(0), NOW)).thenReturn(new Result(Outcome.SENT, true, true));
        when(service.process(users.get(1), NOW)).thenReturn(new Result(Outcome.SENT, true, false));
        when(service.process(users.get(2), NOW)).thenReturn(Result.SKIPPED);
        when(service.process(users.get(3), NOW)).thenReturn(Result.NOT_DUE);
        when(service.process(users.get(4), NOW)).thenReturn(new Result(Outcome.FAILED, true, true));
        when(service.process(users.get(5), NOW)).thenThrow(new IllegalStateException("db down"));

        assertThat(job(true).run()).isEqualTo(new Summary(2, 1, 1, 2, 3, 1));
    }

    @Test
    void everyUserSeesTheSameRunStart() {
        List<Candidate> users = candidates(1, 2);
        when(service.candidates()).thenReturn(users);
        when(service.process(any(), any())).thenReturn(Result.NOT_DUE);

        job(true).run();

        verify(service).process(users.get(0), NOW);
        verify(service).process(eq(users.get(1)), eq(NOW));
    }

    @Test
    void disabledTheScheduleDoesNothing() {
        job(false).sendAll();

        verifyNoInteractions(service);
    }

    @Test
    void enabledWithoutAMailHostFailsAtStartup() {
        assertThatThrownBy(() -> new DigestJob(service, Clock.systemUTC(),
                new DigestProperties(true, 8, 2, "digest@example.test"), ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host");
    }

    @Test
    void enabledWithoutASenderIsRejected() {
        assertThatThrownBy(() -> new DigestProperties(true, 8, 2, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");
    }
}
