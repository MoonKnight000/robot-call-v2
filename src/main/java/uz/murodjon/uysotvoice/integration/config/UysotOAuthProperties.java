package uz.murodjon.uysotvoice.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Uysot's OAuth2 endpoints (§11 settings, authorization-code flow — confirmed to work
 * like Google's: {@code client_id}/{@code client_secret}/{@code redirect_uri}/{@code
 * state}). Global: one Uysot instance, so every company's OAuth app talks to the same
 * {@code authorizeUrl}/{@code tokenUrl} — only {@code client_id}/{@code client_secret}
 * differ per company (entered via {@code PUT /api/settings/integrations/uysot}).
 *
 * <p><strong>The real values are not yet known</strong> — blank by default. {@link
 * uz.murodjon.uysotvoice.integration.service.CrmIntegrationService} refuses to build an
 * authorize URL or exchange a code while either is blank, so this feature stays inert
 * (not broken) until an operator supplies Uysot's actual endpoints.
 */
@ConfigurationProperties(prefix = "voice-agent.integration.uysot")
public record UysotOAuthProperties(
        String authorizeUrl,
        String tokenUrl,
        String redirectUri
) {

    public boolean configured() {
        return authorizeUrl != null && !authorizeUrl.isBlank()
                && tokenUrl != null && !tokenUrl.isBlank()
                && redirectUri != null && !redirectUri.isBlank();
    }
}
