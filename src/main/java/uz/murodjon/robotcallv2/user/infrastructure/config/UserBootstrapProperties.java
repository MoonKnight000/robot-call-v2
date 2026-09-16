package uz.murodjon.robotcallv2.user.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The first admin account, seeded once on startup when the default company still has no
 * users ({@code UserBootstrap}). Bound from {@code voice-agent.user.*} (ROADMAP E.1).
 *
 * <p>It exists because the rest of the accounts are created through the API, and there
 * would otherwise be nobody able to call {@code POST /api/auth/login} to create them. A
 * blank username, email or password seeds nothing — fail-closed, rather than a deployment
 * shipping a well-known default account. So does a password shorter than 10 characters or
 * still reading {@code .env.example}'s CHANGE_ME placeholder: that one is public, and the
 * account it would create is a SUPERADMIN.
 */
@ConfigurationProperties(prefix = "voice-agent.user")
public record UserBootstrapProperties(
        String bootstrapAdminName,
        String bootstrapAdminUsername,
        String bootstrapAdminEmail,
        String bootstrapAdminPassword
) {

    private static final int MIN_PASSWORD_LENGTH = 10;

    public boolean configured() {
        return bootstrapAdminUsername != null && !bootstrapAdminUsername.isBlank()
                && bootstrapAdminEmail != null && !bootstrapAdminEmail.isBlank()
                && bootstrapAdminPassword != null && bootstrapAdminPassword.strip().length() >= MIN_PASSWORD_LENGTH
                && !bootstrapAdminPassword.contains("CHANGE_ME");
    }
}
