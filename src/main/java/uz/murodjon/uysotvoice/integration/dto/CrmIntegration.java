package uz.murodjon.uysotvoice.integration.dto;

import uz.murodjon.uysotvoice.integration.enums.CrmIntegrationStatus;
import uz.murodjon.uysotvoice.integration.enums.CrmProvider;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/settings/integrations} row (§11) — never carries tokens, only
 * whether the company has declared its app identity/grants and whether the OAuth
 * dance has completed.
 */
public record CrmIntegration(
        long companyId,
        CrmProvider provider,
        String appName,
        List<CrmGrant> grants,
        CrmIntegrationStatus status,
        Instant connectedAt
) {
}
