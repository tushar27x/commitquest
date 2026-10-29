package com.tushar27x.insightshub.it;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.tushar27x.insightshub.config.InsightsHubProperties;
import com.tushar27x.insightshub.notify.DigestIntro;
import com.tushar27x.insightshub.notify.DigestProperties;
import com.tushar27x.insightshub.notify.DigestService;
import com.tushar27x.insightshub.notify.DigestService.Outcome;
import com.tushar27x.insightshub.notify.DigestWriter;
import com.tushar27x.insightshub.notify.EmailChannel;
import com.tushar27x.insightshub.repository.DigestRepository;
import com.tushar27x.insightshub.repository.DigestRepository.Candidate;
import com.tushar27x.insightshub.repository.DigestRepository.Status;
import com.tushar27x.insightshub.repository.HeroRepository;
import com.tushar27x.insightshub.security.JwtService;
import com.tushar27x.insightshub.security.UnsubscribeTokens;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.thymeleaf.ITemplateEngine;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The digest (B7) on a real Postgres, with an in-process SMTP server (GreenMail) and the real AI layer, whose
 * LLM URL is a closed port in ITs (so this also proves an AI outage still sends the email). The shared context
 * keeps the digest off and its cron at "-": the service is built here around a GreenMail channel, and only this
 * class's users are processed, never the whole table (other ITs' events stay unsent).
 */
@IntegrationTest
class DigestIT {

    static final long USER = 9_000_601L; // block 9_000_6xx, see IntegrationTest
    static final long OFF = 9_000_602L;
    /** 09:00 in Kolkata */
    static final Instant NOW = Instant.parse("2026-10-29T03:30:00Z");
    static final LocalDate YESTERDAY = LocalDate.parse("2026-10-28");

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    @Autowired
    DigestRepository digests;
    @Autowired
    DigestWriter writer;
    @Autowired
    HeroRepository heroes;
    @Autowired
    DigestIntro intro;
    @Autowired
    UnsubscribeTokens tokens;
    @Autowired
    JwtService jwtService;
    @Autowired
    ITemplateEngine templates;
    @Autowired
    InsightsHubProperties appProperties;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    WebApplicationContext context;

    final DigestProperties properties = new DigestProperties(true, 8, 2, "Insight Hub <digest@example.test>");

    @BeforeEach
    void freshUsers() {
        jdbc.update("DELETE FROM v2.users WHERE github_id BETWEEN 9000600 AND 9000699"); // cascades to events, digests
        for (long id : List.of(USER, OFF)) {
            jdbc.update("""
                    INSERT INTO v2.users (github_id, login, email, timezone, alerts_enabled)
                    VALUES (?, ?, ?, 'Asia/Kolkata', ?)""", id, "it-digest-" + id, "it" + id + "@example.test", id == USER);
            jdbc.update("INSERT INTO v2.hero_state (user_id, level, xp, lives, streak, last_evaluated_on) "
                    + "VALUES (?, 3, 20, 1, 0, ?)", id, YESTERDAY);
            event(id, "RESTED", YESTERDAY.minusDays(1), "{}");
            event(id, "LIFE_LOST", YESTERDAY, "{\"lives\": 1, \"reason\": \"NO_ACTIVITY\"}");
            event(id, "LEVEL_UP", YESTERDAY.minusDays(10), "{\"from\": 2, \"to\": 3}"); // stale
        }
    }

    private void event(long userId, String type, LocalDate on, String details) {
        jdbc.update("INSERT INTO v2.hero_events (user_id, type, on_date, details) VALUES (?, ?, ?, CAST(? AS jsonb))",
                userId, type, on, details);
    }

    private DigestService service(JavaMailSender sender, Instant now) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("mailSender", sender));
        ObjectProvider<JavaMailSender> provider = beans.getBeanProvider(JavaMailSender.class);
        return new DigestService(digests, writer, heroes, intro, new EmailChannel(provider, templates, properties),
                tokens, Clock.fixed(now, ZoneOffset.UTC), properties, appProperties);
    }

    private static JavaMailSender smtpAt(int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(port);
        return sender;
    }

    private Candidate candidate(long id) {
        return digests.findCandidates().stream().filter(c -> c.userId() == id).findFirst().orElse(null);
    }

    private List<Map<String, Object>> digestRows(long userId) {
        return jdbc.queryForList("SELECT status, channel, event_count, ai_intro FROM v2.digests WHERE user_id = ? "
                + "ORDER BY status", userId);
    }

    private long unsent(long userId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM v2.hero_events WHERE user_id = ? AND digest_id IS NULL",
                Long.class, userId);
        return count == null ? 0 : count;
    }

    @Test
    void sendsOneEmailThenStampsEveryEventOnce() throws Exception {
        DigestService service = service(smtpAt(smtp.getSmtp().getPort()), NOW);

        var result = service.process(candidate(USER), NOW);

        assertThat(result.outcome()).isEqualTo(Outcome.SENT);
        assertThat(result.introFromAi()).isFalse(); // the LLM is unreachable in ITs: the static intro went out

        MimeMessage[] received = smtp.getReceivedMessages();
        assertThat(received).hasSize(1);
        MimeMessage mail = received[0];
        assertThat(mail.getSubject()).isEqualTo("Your hero is down to 1 heart");
        assertThat(mail.getAllRecipients()[0].toString()).isEqualTo("it" + USER + "@example.test");
        assertThat(mail.getHeader("List-Unsubscribe-Post")).containsExactly("List-Unsubscribe=One-Click");
        String link = mail.getHeader("List-Unsubscribe")[0];
        assertThat(link).startsWith("<http://localhost:8080/api/notifications/unsubscribe?token=");
        assertThat(tokens.verify(link.substring(link.indexOf("token=") + 6, link.length() - 1))).hasValue(USER);
        assertThat(GreenMailUtil.getBody(mail)).contains("A quiet day cost a heart: 1 heart left.");

        assertThat(digestRows(USER)).containsExactly(
                Map.of("status", "SENT", "channel", "EMAIL", "event_count", 2, "ai_intro", false),
                mapWithNullChannel("SKIPPED", 1));
        assertThat(unsent(USER)).isZero();

        // the user has no unsent events now, so the next hour finds nothing
        assertThat(candidate(USER)).isNull();
    }

    @Test
    void oneEmailPerLocalDay() {
        DigestService service = service(smtpAt(smtp.getSmtp().getPort()), NOW);
        assertThat(service.process(candidate(USER), NOW).outcome()).isEqualTo(Outcome.SENT);

        event(USER, "LEVEL_DOWN", YESTERDAY, "{\"from\": 3, \"to\": 2}"); // settled late, same local day
        Candidate again = candidate(USER);
        assertThat(again.lastSentAt()).isEqualTo(NOW);
        assertThat(service.process(again, NOW.plusSeconds(3600)).outcome()).isEqualTo(Outcome.NOT_DUE);

        Instant tomorrowNine = NOW.plusSeconds(24 * 3600);
        assertThat(service(smtpAt(smtp.getSmtp().getPort()), tomorrowNine).process(candidate(USER), tomorrowNine)
                .outcome()).isEqualTo(Outcome.SENT);
        assertThat(smtp.getReceivedMessages()).hasSize(2);
    }

    @Test
    void alertsOffStampsSkippedWithoutAnEmail() {
        assertThat(service(smtpAt(smtp.getSmtp().getPort()), NOW).process(candidate(OFF), NOW).outcome())
                .isEqualTo(Outcome.SKIPPED);

        assertThat(smtp.getReceivedMessages()).isEmpty();
        assertThat(digestRows(OFF)).containsExactly(mapWithNullChannel("SKIPPED", 3));
        assertThat(unsent(OFF)).isZero();
    }

    @Test
    void aFailedSendLeavesEverythingUnsentForTheNextRun() {
        JavaMailSender closed = smtpAt(9); // the discard port: nothing listens there

        assertThat(service(closed, NOW).process(candidate(USER), NOW).outcome()).isEqualTo(Outcome.FAILED);

        assertThat(digestRows(USER)).isEmpty();
        assertThat(unsent(USER)).isEqualTo(3);
        assertThat(service(smtpAt(smtp.getSmtp().getPort()), NOW).process(candidate(USER), NOW).outcome())
                .isEqualTo(Outcome.SENT);
    }

    @Test
    void stampingOnlyTakesUnsentEvents() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM v2.hero_events WHERE user_id = ? ORDER BY id", Long.class, USER);
        long first = digests.insertDigest(USER, Status.SKIPPED, null, ids.size(), false, NOW);
        long second = digests.insertDigest(USER, Status.SKIPPED, null, ids.size(), false, NOW);

        assertThat(digests.stamp(first, USER, ids)).isEqualTo(3);
        assertThat(digests.stamp(second, USER, ids)).isZero();
        assertThat(digests.stamp(second, OFF, ids)).isZero(); // another user's events are never stamped
        assertThat(jdbc.queryForList("SELECT DISTINCT digest_id FROM v2.hero_events WHERE user_id = ?", Long.class, USER))
                .containsExactly(first);
    }

    @Test
    void theSchemaRefusesInconsistentDigests() {
        assertThatThrownBy(() -> digests.insertDigest(USER, Status.SENT, null, 1, false, NOW))
                .isInstanceOf(DataIntegrityViolationException.class); // SENT needs a channel
        assertThatThrownBy(() -> digests.insertDigest(USER, Status.SKIPPED, "EMAIL", 1, false, NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> digests.insertDigest(USER, Status.SKIPPED, null, 1, true, NOW))
                .isInstanceOf(DataIntegrityViolationException.class); // no intro without an email
        assertThatThrownBy(() -> jdbc.update("UPDATE v2.hero_events SET digest_id = -1 WHERE user_id = ?", USER))
                .isInstanceOf(DataIntegrityViolationException.class); // the V6 foreign key
    }

    @Test
    void theUnsubscribeLinkConfirmsThenTurnsAlertsOff() throws Exception {
        // Built from the shared context: @AutoConfigureMockMvc would start a second one (and new containers).
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String path = "/api/notifications/unsubscribe";
        String token = tokens.issue(USER);

        mvc.perform(get(path).param("token", token).with(request -> {
                    request.setRemoteAddr("10.6.0.1");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<form method=\"post\"")));
        assertThat(alertsEnabled(USER)).isTrue(); // a prefetching mail scanner changes nothing

        mvc.perform(post(path).param("token", jwtService.issue(USER, "it").value()).with(request -> {
            request.setRemoteAddr("10.6.0.2");
            return request;
        })).andExpect(status().isBadRequest());
        assertThat(alertsEnabled(USER)).isTrue();

        // no cookie, no Authorization header, no CSRF token
        mvc.perform(post(path).param("token", token).with(request -> {
                    request.setRemoteAddr("10.6.0.3");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Digest emails are off")));
        assertThat(alertsEnabled(USER)).isFalse();
    }

    private boolean alertsEnabled(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT alerts_enabled FROM v2.users WHERE github_id = ?",
                Boolean.class, id));
    }

    private static Map<String, Object> mapWithNullChannel(String status, int count) {
        Map<String, Object> row = new HashMap<>();
        row.put("status", status);
        row.put("channel", null);
        row.put("event_count", count);
        row.put("ai_intro", false);
        return row;
    }
}
