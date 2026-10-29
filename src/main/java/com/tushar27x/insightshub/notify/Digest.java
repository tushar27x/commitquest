package com.tushar27x.insightshub.notify;

import com.tushar27x.insightshub.hero.HeroState;
import com.tushar27x.insightshub.notify.DigestContent.Line;

import java.util.List;

/**
 * One user's digest, ready for a {@link NotificationChannel}.
 *
 * @param hero           the hero now, or null if it has none (it always has one when events exist)
 * @param unsubscribeUrl the signed one-click link ({@code GET} confirms, {@code POST} turns alerts off)
 * @param appUrl         the web app's Home screen
 * @param settingsUrl    the web app's Settings screen, where alerts can be turned back on
 */
public record Digest(long userId, String login, String email, String subject, String intro,
                     List<Line> headlines, List<Line> also, HeroState hero,
                     String unsubscribeUrl, String appUrl, String settingsUrl) {

    public Digest {
        headlines = List.copyOf(headlines);
        also = List.copyOf(also);
    }
}
