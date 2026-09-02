package uz.murodjon.robotcallv2.integration.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Uysot's OAuth2 endpoints and this platform's own registered app (§11 settings).
 */
@ConfigurationProperties(prefix = "voice-agent.integration.uysot")
public record UysotOAuthProperties(
        String authorizeUrl,
        String tokenUrl,
        String revokeUrl,
        String redirectUri,
        String clientId,
        String clientSecret
) {
    public boolean configured() {
        return authorizeUrl != null && !authorizeUrl.isBlank()
                && tokenUrl != null && !tokenUrl.isBlank()
                && redirectUri != null && !redirectUri.isBlank()
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
