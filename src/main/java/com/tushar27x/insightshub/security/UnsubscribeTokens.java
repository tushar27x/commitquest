package com.tushar27x.insightshub.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tushar27x.insightshub.config.InsightsHubProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;

/**
 * The digest's signed unsubscribe links (Phase 12): an HS256 JWT with {@code sub} = GitHub id,
 * {@code aud = "unsubscribe"} and a year to live.
 * <p>
 * It's signed with a key <b>derived</b> from {@code JWT_SECRET} (HMAC-SHA256 of a fixed label), not with the
 * secret itself. So a login token can't pass as an unsubscribe token, and an unsubscribe token, which sits in
 * mailboxes for a year, can't pass as a login token, whatever the claims say. Rotating {@code JWT_SECRET}
 * invalidates every link already mailed; the Settings toggle still works.
 */
@Slf4j
@Component
public class UnsubscribeTokens {

    static final String AUDIENCE = "unsubscribe";
    static final Duration TTL = Duration.ofDays(365);
    private static final String KEY_LABEL = "insightshub-unsubscribe-v1";

    private final Clock clock;
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    @Autowired
    public UnsubscribeTokens(InsightsHubProperties properties, Clock clock) {
        this(properties.jwtSecret(), clock);
    }

    UnsubscribeTokens(String jwtSecret, Clock clock) {
        SecretKey key = derivedKey(jwtSecret);
        this.clock = clock;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(JwtConfig.ALGORITHM).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(); // 60 s skew, like the login tokens
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator(JwtConfig.ISSUER),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(AUDIENCE))));
    }

    public String issue(long githubId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(Long.toString(githubId))
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .build();
        JwsHeader header = JwsHeader.with(JwtConfig.ALGORITHM).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** @return the GitHub id, or empty for a token that's malformed, forged, expired or not for unsubscribing */
    public OptionalLong verify(String token) {
        if (token == null || token.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(decoder.decode(token).getSubject()));
        } catch (JwtException | NumberFormatException e) {
            log.debug("Unsubscribe token rejected: {}", e.getClass().getSimpleName()); // never the token itself
            return OptionalLong.empty();
        }
    }

    private static SecretKey derivedKey(String jwtSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(JwtConfig.signingKey(jwtSecret));
            return new SecretKeySpec(mac.doFinal(KEY_LABEL.getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }
}
