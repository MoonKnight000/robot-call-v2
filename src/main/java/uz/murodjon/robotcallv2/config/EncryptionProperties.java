package uz.murodjon.robotcallv2.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The key {@link uz.murodjon.robotcallv2.shared.util.SecretCipher} encrypts with (§11
 * integrations — Uysot OAuth {@code client_secret}/tokens, which must be read back, unlike
 * {@code shared.util.Tokens#hash}'s one-way hashes). A base64-encoded 32-byte AES-256 key,
 * e.g. {@code openssl rand -base64 32}. Blank disables anything that depends on it —
 * fail-closed, same posture as {@code SecurityProperties}.
 */
@ConfigurationProperties(prefix = "voice-agent.encryption")
public record EncryptionProperties(
        String secretKey
) {

    public boolean configured() {
        return secretKey != null && !secretKey.isBlank();
    }
}
