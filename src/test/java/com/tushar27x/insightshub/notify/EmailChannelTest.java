package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.hero.HeroEvent;
import com.tushar27x.insightshub.hero.HeroEventType;
import com.tushar27x.insightshub.hero.HeroState;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The email itself: headers, both parts, escaping. Nothing is sent: {@code createMimeMessage} needs no server. */
class EmailChannelTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 26);
    private static final String UNSUBSCRIBE = "http://localhost:8080/api/notifications/unsubscribe?token=signed-test-token";

    private static SpringTemplateEngine templates() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (sender != null) {
            beans.addBean("mailSender", sender);
        }
        return beans.getBeanProvider(JavaMailSender.class);
    }

    private static EmailChannel channel(JavaMailSender sender) {
        return new EmailChannel(provider(sender), templates(), new DigestProperties(true, 8, 2, "Insight Hub <digest@example.test>"));
    }

    private static Digest digest(String login) {
        DigestContent content = DigestContent.of(List.of(
                new HeroEvent(HeroEventType.RESTED, MON, Map.of()),
                new HeroEvent(HeroEventType.LEVEL_UP, MON.plusDays(1), Map.of("from", 2, "to", 3))));
        return new Digest(1L, login, "octocat@example.test", content.subject(), "Nice work this week.",
                content.headlines(), content.also(), new HeroState(3, 0, 1, 5, MON.plusDays(1)),
                UNSUBSCRIBE, "http://localhost:3000/home", "http://localhost:3000/settings");
    }

    @Test
    void anEmailWithBothPartsAndTheOneClickHeaders() throws Exception {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();

        MimeMessage message = channel(sender).message(sender, digest("octocat"));
        message.saveChanges();

        assertThat(message.getSubject()).isEqualTo("Your hero reached level 3");
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("digest@example.test");
        assertThat(((InternetAddress) message.getAllRecipients()[0]).getAddress()).isEqualTo("octocat@example.test");
        assertThat(message.getHeader("List-Unsubscribe")).containsExactly("<" + UNSUBSCRIBE + ">");
        assertThat(message.getHeader("List-Unsubscribe-Post")).containsExactly("List-Unsubscribe=One-Click");

        List<String> parts = new ArrayList<>();
        collect(message, parts);
        String text = parts.stream().filter(part -> part.startsWith("text/plain|")).findFirst().orElseThrow();
        String html = parts.stream().filter(part -> part.startsWith("text/html|")).findFirst().orElseThrow();

        assertThat(text).contains("Hi octocat,", "Nice work this week.", "- Tue 27 Oct: Your hero reached level 3.",
                "Also:", "- Mon 26 Oct: A rest day", "Your hero now: level 3, 1 heart, streak 5.",
                "Turn them off: " + UNSUBSCRIBE);
        assertThat(html).contains("Nice work this week.", "Your hero reached level 3.", "A rest day",
                "href=\"http://localhost:3000/home\"", "href=\"http://localhost:3000/settings\"",
                "href=\"" + UNSUBSCRIBE + "\"", "1</span>", "heart</span>");
    }

    @Test
    void theHtmlEscapesWhatItShows() {
        String html = channel(new JavaMailSenderImpl()).html(digest("<script>alert(1)</script>"));

        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void aFailedSendNamesTheFailureNotTheAddress() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        doThrow(new MailSendException("refused for octocat@example.test")).when(sender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> channel(sender).send(digest("octocat")))
                .isInstanceOf(NotificationException.class)
                .hasMessage("SMTP send failed: MailSendException");
    }

    @Test
    void noMailSenderIsAFailedSend() {
        assertThatThrownBy(() -> channel(null).send(digest("octocat")))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("spring.mail.host");
    }

    private static void collect(Part part, List<String> out) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                collect(multipart.getBodyPart(i), out);
            }
        } else if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
            String type = part.isMimeType("text/plain") ? "text/plain" : "text/html";
            out.add(type + "|" + content(part));
        }
    }

    private static String content(Part part) throws IOException, jakarta.mail.MessagingException {
        return String.valueOf(part.getContent());
    }
}
