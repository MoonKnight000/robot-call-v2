package uz.murodjon.robotcallv2.user.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from voice-agent.user.* (ROADMAP E.1).
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
