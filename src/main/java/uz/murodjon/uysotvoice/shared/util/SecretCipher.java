package uz.murodjon.uysotvoice.shared.util;

import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.config.EncryptionProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encrypt/decrypt for secrets that must be read back (§11 integrations —
 * Uysot OAuth {@code client_secret}, access/refresh tokens). Unlike {@link Tokens#hash},
 * which is one-way and fine for values only ever compared, an OAuth secret has to be
 * sent back to the provider on every token refresh, so it cannot be hashed.
 *
 * <p>Ciphertext is stored as {@code base64(iv || ciphertext+tag)} — a fresh random IV
 * per call, prepended so decryption never needs it passed separately.
 */
@Component
public class SecretCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public SecretCipher(EncryptionProperties props) {
        this.key = props.configured() ? new SecretKeySpec(Base64.getDecoder().decode(props.secretKey()), "AES") : null;
    }

    public boolean available() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        requireConfigured();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("secret encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        requireConfigured();
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("secret decryption failed", e);
        }
    }

    private void requireConfigured() {
        if (key == null) {
            throw new IllegalStateException(
                    "voice-agent.encryption.secret-key is not set — cannot encrypt/decrypt secrets");
        }
    }
}
