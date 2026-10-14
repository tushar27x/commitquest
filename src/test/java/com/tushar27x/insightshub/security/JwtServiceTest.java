package com.tushar27x.insightshub.security;

import com.tushar27x.insightshub.security.JwtService.IssuedToken;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtDecoder decoder = JwtConfig.decoder(SecurityTestData.JWT_SECRET);

    private static JwtService serviceAt(Instant now) {
        return new JwtService(JwtConfig.encoder(SecurityTestData.JWT_SECRET),
                Clock.fixed(now, ZoneOffset.UTC), SecurityTestData.authProperties());
    }

    @Test
    void issuesTokenTheDecoderAccepts() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        IssuedToken token = serviceAt(now).issue(583231L, "octocat");
        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo("583231");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("octocat");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("insightshub");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofHours(24)));
        assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt());
    }

    @Test
    void rejectsExpiredToken() {
        IssuedToken token = serviceAt(Instant.now().minus(Duration.ofHours(25))).issue(1L, "old");

        assertThatThrownBy(() -> decoder.decode(token.value()))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtService forger = new JwtService(JwtConfig.encoder("another-secret-that-is-also-32-bytes-or-more"),
                Clock.systemUTC(), SecurityTestData.authProperties());

        assertThatThrownBy(() -> decoder.decode(forger.issue(1L, "mallory").value()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> JwtConfig.encoder("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
