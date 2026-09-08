package uz.murodjon.robotcallv2.integration.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Uysot's OAuth2 endpoints and this platform's own registered app (§11 settings).
 *
 * @param redirectUri          where Uysot sends the browser back with {@code ?code=&state=}.
 *                             Must be one of the URIs registered on the Uysot application,
 *                             character for character, and is re-sent at the token exchange
 * @param callbackRedirectUrl  where the browser is sent <em>after</em> the token exchange —
 *                             a page in this platform's own UI. {@code redirectUri} has to
 *                             be a backend address (the exchange needs the client secret and
 *                             the code must not pass through a browser page), so without
 *                             this the person who just approved the connection would be
 *                             left looking at an API response. Blank sends them to the app
 *                             root instead
 */
@ConfigurationProperties(prefix = "voice-agent.integration.uysot")
public record UysotOAuthProperties(
        String authorizeUrl,
        String tokenUrl,
        String revokeUrl,
        String redirectUri,
        String callbackRedirectUrl,
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
