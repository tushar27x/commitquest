package com.tushar27x.insightshub.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tushar27x.insightshub.config.InsightsHubProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * HS256 JWTs signed with {@code JWT_SECRET}. The app both issues and validates them,
 * so a shared secret is enough (no key pair or JWKS endpoint).
 */
@Configuration
public class JwtConfig {

    static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;
    static final String ISSUER = "insightshub";

    /** HS256 needs a key at least as long as the hash: 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    JwtEncoder jwtEncoder(InsightsHubProperties properties) {
        return encoder(properties.jwtSecret());
    }

    @Bean
    JwtDecoder jwtDecoder(InsightsHubProperties properties) {
        return decoder(properties.jwtSecret());
    }

    public static JwtEncoder encoder(String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(secret)));
    }

    /** Checks the signature, {@code exp}/{@code nbf} (60 s skew) and that we issued it. */
    public static JwtDecoder decoder(String secret) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(secret))
                .macAlgorithm(ALGORITHM)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    static SecretKey signingKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
