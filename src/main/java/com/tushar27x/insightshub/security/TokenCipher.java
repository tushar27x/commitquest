package com.tushar27x.insightshub.security;

import com.tushar27x.insightshub.config.InsightsHubProperties;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts GitHub tokens at rest with AES-256-GCM (replaces v1's Fernet).
 * <p>
 * Output: Base64({@code iv || ciphertext || tag}), with a fresh random 12-byte IV per call.
 * The owner's GitHub id is bound as associated data, so a ciphertext copied onto another
 * user's row fails to decrypt instead of handing that user someone else's token.
 */
@Component
public class TokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(InsightsHubProperties properties) {
        this.key = new SecretKeySpec(decodeKey(properties.encryptionKey()), "AES");
    }

    public String encrypt(String plaintext, long ownerId) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, ownerId);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Token encryption failed", e);
        }
    }

    public String decrypt(String encrypted, long ownerId) {
        byte[] data = Base64.getDecoder().decode(encrypted);
        if (data.length < IV_BYTES + TAG_BITS / 8) {
            throw new IllegalArgumentException("Encrypted token is too short");
        }
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(data, IV_BYTES), ownerId);
            byte[] plaintext = cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("Token failed authentication: wrong key, wrong owner or tampered data", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Token decryption failed", e);
        }
    }

    private Cipher cipher(int mode, byte[] iv, long ownerId) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(ByteBuffer.allocate(Long.BYTES).putLong(ownerId).array());
        return cipher;
    }

    /** Accepts standard or URL-safe Base64 (e.g. {@code openssl rand -base64 32}). Never echoes the key. */
    static byte[] decodeKey(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException notStandard) {
            try {
                bytes = Base64.getUrlDecoder().decode(base64);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("ENCRYPTION_KEY is not valid Base64");
            }
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException("ENCRYPTION_KEY must decode to " + KEY_BYTES
                    + " bytes (AES-256), got " + bytes.length);
        }
        return bytes;
    }
}
