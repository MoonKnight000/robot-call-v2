package uz.murodjon.robotcallv2.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from voice-agent.security.jwt.* (ROADMAP E.1).
 */
@ConfigurationProperties(prefix = "voice-agent.security.jwt")
public record JwtProperties(String secret, Integer expiryMinutes) {

    private static final int DEFAULT_EXPIRY_MINUTES = 12 * 60;

    public boolean configured() {
        return secret != null && !secret.isBlank();
    }

    public int expiryMinutesOrDefault() {
        return expiryMinutes == null || expiryMinutes <= 0 ? DEFAULT_EXPIRY_MINUTES : expiryMinutes;
    }
}
