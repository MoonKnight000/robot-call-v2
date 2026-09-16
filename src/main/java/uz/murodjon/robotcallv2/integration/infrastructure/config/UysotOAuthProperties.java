package uz.murodjon.robotcallv2.integration.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Uysot's OAuth2 endpoints and this platform's own registered app (§11 settings, per
 * https://apidoc.app.uysot.uz/docs/guides/oauth).
 *
 * @param authorizeUrl         the consent page. Its host is handed out at application
 *                             registration and the guide says to use that value rather
 *                             than the documented example, which makes this the setting
 *                             here most likely to need overriding
 * @param redirectUri          where Uysot sends the browser back with {@code ?code=&state=}.
 *                             Must be one of the URIs registered on the Uysot application,
 *                             character for character — the authorize request is rejected
 *                             otherwise ({@code 6902 invalid redirect_uri}) — and is
 *                             re-sent at the token exchange. It is the frontend's
 *                             integrations page, which navigates the browser on to
 *                             {@code IntegrationController#callback} with the same
 *                             {@code code} and {@code state}
 * @param callbackRedirectUrl  where the browser is sent <em>after</em> the token exchange —
 *                             a page in this platform's own UI, so the person who just
 *                             approved the connection is not left looking at an API
 *                             response. The page is opened with
 *                             {@code ?crm=connected}, or {@code ?crm=error&reason=<ErrorCode>}
 *                             when the exchange failed. Blank sends them to the app root
 *                             instead
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
