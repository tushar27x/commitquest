package com.tushar27x.insightshub.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {

    private static final String TOKEN = "gho_exampleGitHubToken1234567890";
    private static final long OWNER = 583231L;

    private final TokenCipher cipher = new TokenCipher(SecurityTestData.appProperties());

    @Test
    void roundTrips() {
        String encrypted = cipher.encrypt(TOKEN, OWNER);

        assertThat(encrypted).doesNotContain(TOKEN);
        assertThat(cipher.decrypt(encrypted, OWNER)).isEqualTo(TOKEN);
    }

    @Test
    void freshIvEveryTime() {
        String first = cipher.encrypt(TOKEN, OWNER);
        String second = cipher.encrypt(TOKEN, OWNER);

        assertThat(first).isNotEqualTo(second);
        assertThat(Base64.getDecoder().decode(first)).hasSize(12 + TOKEN.length() + 16); // iv + ciphertext + tag
    }

    @Test
    void rejectsTamperedCiphertext() {
        byte[] data = Base64.getDecoder().decode(cipher.encrypt(TOKEN, OWNER));
        data[20] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(data), OWNER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed authentication");
    }

    @Test
    void rejectsCiphertextMovedToAnotherUser() {
        String encrypted = cipher.encrypt(TOKEN, OWNER);

        assertThatThrownBy(() -> cipher.decrypt(encrypted, OWNER + 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsWrongKey() {
        TokenCipher other = new TokenCipher(SecurityTestData.appProperties(SecurityTestData.OTHER_ENCRYPTION_KEY));

        assertThatThrownBy(() -> other.decrypt(cipher.encrypt(TOKEN, OWNER), OWNER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsTruncatedInput() {
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(new byte[10]), OWNER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsUrlSafeBase64Key() {
        byte[] key = new byte[32];
        key[0] = (byte) 0xfb; // encodes to '-' / '_' in the URL-safe alphabet
        key[1] = (byte) 0xff;
        String urlSafe = Base64.getUrlEncoder().encodeToString(key);

        assertThat(urlSafe).containsAnyOf("-", "_");
        assertThat(TokenCipher.decodeKey(urlSafe)).isEqualTo(key);
    }

    @Test
    void rejectsKeyOfWrongLengthWithoutEchoingIt() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> TokenCipher.decodeKey(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes")
                .hasMessageNotContaining(shortKey);
    }
}
