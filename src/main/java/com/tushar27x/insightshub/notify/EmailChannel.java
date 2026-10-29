package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.notify.DigestContent.Line;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Sends a digest as one email over SMTP ({@code spring.mail.*}): an HTML part rendered from
 * {@code templates/email/digest.html} and a plain-text part, plus the RFC 8058 one-click unsubscribe headers
 * ({@code List-Unsubscribe} + {@code List-Unsubscribe-Post}), which Gmail and Yahoo expect from bulk senders.
 */
@Component
public class EmailChannel implements NotificationChannel {

    static final String NAME = "EMAIL";
    static final String TEMPLATE = "email/digest";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final ITemplateEngine templates;
    private final String from;

    public EmailChannel(ObjectProvider<JavaMailSender> mailSender, ITemplateEngine templates,
                        DigestProperties properties) {
        this.mailSender = mailSender;
        this.templates = templates;
        this.from = properties.from();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void send(Digest digest) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new NotificationException("No mail sender: spring.mail.host isn't set", null);
        }
        try {
            sender.send(message(sender, digest));
        } catch (MailException | MessagingException e) {
            // the exception names the failure, never the address or the body
            throw new NotificationException("SMTP send failed: " + e.getClass().getSimpleName(), e);
        }
    }

    MimeMessage message(JavaMailSender sender, Digest digest) throws MessagingException {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(digest.email());
        helper.setSubject(digest.subject());
        helper.setText(plainText(digest), html(digest));
        message.setHeader("List-Unsubscribe", "<" + digest.unsubscribeUrl() + ">");
        message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        return message;
    }

    String html(Digest digest) {
        Context context = new Context();
        context.setVariable("digest", digest);
        return templates.process(TEMPLATE, context);
    }

    static String plainText(Digest digest) {
        StringBuilder text = new StringBuilder();
        text.append("Hi ").append(digest.login()).append(",\n\n").append(digest.intro()).append("\n\n");
        lines(text, digest.headlines());
        if (!digest.also().isEmpty()) {
            text.append("\nAlso:\n");
            lines(text, digest.also());
        }
        if (digest.hero() != null) {
            text.append("\nYour hero now: ").append(DigestIntro.describe(digest.hero())).append(".\n");
        }
        text.append("\nSee your hero: ").append(digest.appUrl()).append('\n')
                .append("\nYou get this because digest emails are on in Settings (").append(digest.settingsUrl())
                .append(").\nTurn them off: ").append(digest.unsubscribeUrl()).append('\n');
        return text.toString();
    }

    private static void lines(StringBuilder text, List<Line> lines) {
        lines.forEach(line -> text.append("- ").append(line.day()).append(": ").append(line.text()).append('\n'));
    }
}
