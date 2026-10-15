package uz.murodjon.robotcallv2.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Self-service signup ({@code config/auth.yml}).
 *
 * @param enabled       off by default: an open endpoint that hands out trial money is a
 *                      deployment's decision, not a default
 * @param activationUrl the frontend page that takes {@code ?token=} and calls
 *                      {@code POST /api/auth/activate}
 * @param from          sender address of the activation mail
 */
@ConfigurationProperties(prefix = "voice-agent.security.signup")
public record SignupProperties(
        boolean enabled,
        String activationUrl,
        String from
) {
}
