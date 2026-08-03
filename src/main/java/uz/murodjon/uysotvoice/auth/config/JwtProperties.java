package uz.murodjon.uysotvoice.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code voice-agent.security.jwt.*} (ROADMAP E.1).
 *
 * @param secret        HS256 signing key for user login tokens. Blank means no token can
 *                       ever be issued or verified — fail-closed, same principle as
 *                       {@code voice-agent.security.api-key}.
 * @param expiryMinutes access token lifetime; blank/non-positive defaults to 12 hours.
 *                      No server-side revocation exists yet (stateless JWT) — logout is a
 *                      client-side token discard, see {@code AuthController.logout}.
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
