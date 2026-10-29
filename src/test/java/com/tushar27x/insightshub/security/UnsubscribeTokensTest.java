package com.tushar27x.insightshub.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnsubscribeTokensTest {

    private static final Instant NOW = Instant.parse("2026-10-29T08:35:00Z");

    private static UnsubscribeTokens tokensAt(Instant now) {
        return new UnsubscribeTokens(SecurityTestData.JWT_SECRET, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void aTokenNamesItsUser() {
        String token = tokensAt(NOW).issue(583231L);

        assertThat(tokensAt(NOW).verify(token)).hasValue(583231L);
    }

    @Test
    void aTokenLastsAYear() {
        String token = tokensAt(NOW).issue(7L);

        assertThat(tokensAt(NOW.plus(Duration.ofDays(364))).verify(token)).hasValue(7L);
        assertThat(tokensAt(NOW.plus(Duration.ofDays(366))).verify(token)).isEmpty();
    }

    @Test
    void anUnsubscribeTokenIsNotALoginToken() {
        String token = tokensAt(Instant.now().truncatedTo(ChronoUnit.SECONDS)).issue(7L);
        JwtDecoder loginDecoder = JwtConfig.decoder(SecurityTestData.JWT_SECRET);

        assertThatThrownBy(() -> loginDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void aLoginTokenIsNotAnUnsubscribeToken() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtService logins = new JwtService(JwtConfig.encoder(SecurityTestData.JWT_SECRET),
                Clock.fixed(now, ZoneOffset.UTC), SecurityTestData.authProperties());
        String login = logins.issue(7L, "octocat").value();

        assertThat(tokensAt(now).verify(login)).isEmpty();
    }

    @Test
    void anotherSecretsTokenIsRejected() {
        String foreign = new UnsubscribeTokens(SecurityTestData.JWT_SECRET + "-other", Clock.fixed(NOW, ZoneOffset.UTC))
                .issue(7L);

        assertThat(tokensAt(NOW).verify(foreign)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        UnsubscribeTokens tokens = tokensAt(NOW);

        assertThat(tokens.verify(null)).isEmpty();
        assertThat(tokens.verify("")).isEmpty();
        assertThat(tokens.verify("not-a-jwt")).isEmpty();
        String token = tokens.issue(7L);
        assertThat(tokens.verify(token.substring(0, token.length() - 2))).isEmpty(); // broken signature
    }
}
