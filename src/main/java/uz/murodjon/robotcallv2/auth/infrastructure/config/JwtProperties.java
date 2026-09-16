package uz.murodjon.robotcallv2.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * User login tokens. Bound from {@code voice-agent.security.jwt.*} (ROADMAP E.1).
 *
 * @param secret        HS256 signing key for {@code Authorization: Bearer}, hashed to 256
 *                      bits before use. Blank, shorter than 32 characters or still the
 *                      {@code .env.example} CHANGE_ME placeholder fails closed — nobody can
 *                      log in — because whoever guesses it can mint a token for any company
 *                      with any permission, PLATFORM_ADMIN included.
 * @param expiryMinutes access token lifetime. Logout, a password or role change and a
 *                      block revoke a user's tokens early ({@code TokenRevocationService});
 *                      this bounds how long one lives when none of those happens, and how
 *                      long a leaked one stays usable on an instance that cannot reach Redis.
 */
@ConfigurationProperties(prefix = "voice-agent.security.jwt")
public record JwtProperties(String secret, Integer expiryMinutes) {

    private static final int DEFAULT_EXPIRY_MINUTES = 60;
    private static final int MIN_SECRET_LENGTH = 32;

    public boolean configured() {
        return secret != null && secret.strip().length() >= MIN_SECRET_LENGTH && !secret.contains("CHANGE_ME");
    }

    public int expiryMinutesOrDefault() {
        return expiryMinutes == null || expiryMinutes <= 0 ? DEFAULT_EXPIRY_MINUTES : expiryMinutes;
    }
}
