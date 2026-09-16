package uz.murodjon.robotcallv2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * The key {@link uz.murodjon.robotcallv2.shared.util.SecretCipher} encrypts with (§11
 * integrations — Uysot OAuth {@code client_secret}/tokens, which must be read back, unlike
 * {@code shared.util.Tokens#hash}'s one-way hashes). A base64-encoded 32-byte AES-256 key,
 * e.g. {@code openssl rand -base64 32}. Blank disables anything that depends on it —
 * fail-closed, same posture as {@code SecurityProperties}.
 *
 * <p>Unlike the JWT secret, this cannot be any non-blank string: it is decoded straight
 * into the AES key, so it has to be exactly 32 bytes once base64-decoded.
 *
 * @param secretKey          the key every new ciphertext is written with
 * @param previousSecretKeys keys that were {@code secretKey} before a rotation, oldest last.
 *                           Decryption tries {@code secretKey} first and then each of these,
 *                           so a rotation is: move the old key here, put the new one in
 *                           {@code secretKey}, restart; re-save each secret at leisure and
 *                           drop the old key once nothing decrypts with it any more
 */
@ConfigurationProperties(prefix = "voice-agent.encryption")
public record EncryptionProperties(
        String secretKey,
        List<String> previousSecretKeys
) {

    public boolean configured() {
        return secretKey != null && !secretKey.isBlank();
    }
}
