package uz.murodjon.uysotvoice.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code voice-agent.user.*} (ROADMAP E.1) — the very first admin account,
 * since without one nobody could ever log in to create the rest through the API.
 *
 * @param bootstrapAdminName     display name for the seeded account
 * @param bootstrapAdminUsername login username; blank disables seeding entirely (fail-closed,
 *                               same principle as {@code voice-agent.security.api-key})
 * @param bootstrapAdminEmail    contact email; blank disables seeding entirely
 * @param bootstrapAdminPassword plaintext password, hashed once at startup and never
 *                               logged or stored as given
 */
@ConfigurationProperties(prefix = "voice-agent.user")
public record UserBootstrapProperties(
        String bootstrapAdminName,
        String bootstrapAdminUsername,
        String bootstrapAdminEmail,
        String bootstrapAdminPassword
) {

    public boolean configured() {
        return bootstrapAdminUsername != null && !bootstrapAdminUsername.isBlank()
                && bootstrapAdminEmail != null && !bootstrapAdminEmail.isBlank()
                && bootstrapAdminPassword != null && !bootstrapAdminPassword.isBlank();
    }
}
