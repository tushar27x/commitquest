package com.tushar27x.insightshub.security;

import com.tushar27x.insightshub.auth.AuthProperties;
import com.tushar27x.insightshub.config.InsightsHubProperties;

import java.time.Duration;
import java.util.Base64;

/** Throwaway secrets and properties for tests. Never real values. */
public final class SecurityTestData {

    /**
     * Obviously fake AES keys, built at runtime (bytes 0..31 and 100..131) rather than written as
     * Base64 literals, so secret scanners don't flag them as leaked keys.
     */
    public static final String ENCRYPTION_KEY = sequentialKey(0);
    public static final String OTHER_ENCRYPTION_KEY = sequentialKey(100);
    public static final String JWT_SECRET = "test-jwt-secret-that-is-at-least-32-bytes-long";

    private SecurityTestData() {
    }

    private static String sequentialKey(int first) {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (first + i);
        }
        return Base64.getEncoder().encodeToString(key);
    }

    public static InsightsHubProperties appProperties() {
        return appProperties(ENCRYPTION_KEY);
    }

    public static InsightsHubProperties appProperties(String encryptionKey) {
        return new InsightsHubProperties("http://localhost:3000/", "http://localhost:8080", encryptionKey, JWT_SECRET);
    }

    public static AuthProperties authProperties() {
        return new AuthProperties("client-id", "client-secret", "read:user user:email", Duration.ofHours(24), false);
    }
}
