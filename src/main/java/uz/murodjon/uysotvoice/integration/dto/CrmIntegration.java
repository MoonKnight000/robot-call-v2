package uz.murodjon.uysotvoice.integration.dto;

import uz.murodjon.uysotvoice.integration.enums.CrmIntegrationStatus;
import uz.murodjon.uysotvoice.integration.enums.CrmProvider;

import java.time.Instant;

/**
 * {@code GET /api/settings/integrations} row (§11) — never carries {@code
 * client_secret}/tokens, only whether they are set.
 */
public record CrmIntegration(
        long companyId,
        CrmProvider provider,
        String clientId,
        boolean hasClientSecret,
        CrmIntegrationStatus status,
        Instant connectedAt
) {
}
