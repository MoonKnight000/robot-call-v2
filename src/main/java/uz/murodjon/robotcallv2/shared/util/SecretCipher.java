package uz.murodjon.robotcallv2.shared.util;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.config.EncryptionProperties;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * AES-256-GCM encrypt/decrypt for secrets that must be read back (§11 integrations —
 * Uysot OAuth {@code client_secret}, access/refresh tokens). Unlike {@link Tokens#hash},
 * which is one-way and fine for values only ever compared, an OAuth secret has to be
 * sent back to the provider on every token refresh, so it cannot be hashed.
 *
 * <p>Ciphertext is stored as {@code base64(iv || ciphertext+tag)} — a fresh random IV
 * per call, prepended so decryption never needs it passed separately.
 *
 * <p>Key rotation: everything new is written with the current key; reading tries the
 * current key and then each previous one. GCM's authentication tag tells a wrong key
 * apart from a right one, so no key id has to be stored next to the ciphertext.
 *
 * <p>Every failure here is a {@link ConflictException}: the request was fine, the server's
 * key configuration or a stored row is not — a missing or rotated-away key, a corrupt value.
 */
@Component
public class SecretCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;
    /** Current key first, then the retired ones — the order decryption tries them in. */
    private final List<SecretKeySpec> decryptionKeys;

    public SecretCipher(EncryptionProperties encryptionProperties) {
        this.key = encryptionProperties.configured() ? toKey(encryptionProperties.secretKey()) : null;
        List<SecretKeySpec> keys = new ArrayList<>();
        if (key != null) {
            keys.add(key);
            if (encryptionProperties.previousSecretKeys() != null) {
                for (String previous : encryptionProperties.previousSecretKeys()) {
                    if (previous != null && !previous.isBlank()) {
                        keys.add(toKey(previous));
                    }
                }
            }
        }
        this.decryptionKeys = List.copyOf(keys);
    }

    private static SecretKeySpec toKey(String base64) {
        return new SecretKeySpec(Base64.getDecoder().decode(base64.trim()), "AES");
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
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new ConflictException(ErrorCode.SECRET_ENCRYPTION_FAILED, e);
        }
    }

    public String decrypt(String encoded) {
        requireConfigured();
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new ConflictException(ErrorCode.SECRET_CIPHERTEXT_CORRUPT, e);
        }
        if (combined.length <= IV_LENGTH) {
            throw new ConflictException(ErrorCode.SECRET_CIPHERTEXT_CORRUPT);
        }
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
        for (SecretKeySpec candidate : decryptionKeys) {
            try {
                Cipher cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, candidate, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
                byte[] plaintext = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
                return new String(plaintext, StandardCharsets.UTF_8);
            } catch (AEADBadTagException e) {
                // Not this key — the next one may be the one it was written with.
            } catch (Exception e) {
                throw new ConflictException(ErrorCode.SECRET_DECRYPTION_FAILED, e);
            }
        }
        throw new ConflictException(ErrorCode.SECRET_KEY_MISMATCH);
    }

    private void requireConfigured() {
        if (key == null) {
            throw new ConflictException(ErrorCode.ENCRYPTION_KEY_NOT_SET);
        }
    }
}
