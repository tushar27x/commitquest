package com.tushar27x.insightshub.security;

import com.tushar27x.insightshub.auth.AuthProperties;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Issues the app's own access tokens: {@code sub} = GitHub id, {@code name} = login. */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration ttl;

    public JwtService(JwtEncoder encoder, Clock clock, AuthProperties properties) {
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = properties.tokenTtl();
    }

    public IssuedToken issue(long githubId, String login) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS); // JWT times are whole seconds
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(Long.toString(githubId))
                .claim("name", login)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(JwtConfig.ALGORITHM).build(); // the default would be RS256
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
