package uz.murodjon.uysotvoice.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Uysot's OAuth2 endpoints and this platform's own registered app (§11 settings,
 * authorization-code flow — confirmed against Uysot's real Open API docs, report #10).
 *
 * <p>{@code clientId}/{@code clientSecret} are <strong>global</strong>, not per-company:
 * per Uysot's docs, one registered app (one {@code client_id}/{@code client_secret},
 * tied to one {@code redirectUri}) can serve many companies — which company is
 * connecting is decided by who is logged into Uysot during consent, not by the {@code
 * client_id}. Each company only supplies its own {@code app_name}/{@code grants}
 * (entered via {@code PUT /api/settings/integrations/uysot}, see {@code
 * CrmIntegrationEntity}) — the earlier design where every company entered its own
 * {@code client_id}/{@code client_secret} did not match how the protocol actually works.
 *
 * <p><strong>The real values are not yet known</strong> — blank by default. {@link
 * uz.murodjon.uysotvoice.integration.service.CrmIntegrationService} refuses to build an
 * authorize URL or exchange a code while any is blank, so this feature stays inert
 * (not broken) until an operator supplies Uysot's actual endpoints/app credentials.
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
