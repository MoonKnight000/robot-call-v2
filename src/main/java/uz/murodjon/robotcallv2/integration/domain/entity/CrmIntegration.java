package uz.murodjon.robotcallv2.integration.domain.entity;

import uz.murodjon.robotcallv2.integration.domain.enums.CrmIntegrationStatus;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;

import java.time.Instant;

/**
 * Domain model of crm_integration (§11 settings).
 */
public record CrmIntegration(
        long companyId,
        CrmProvider provider,
        String appName,
        String grantsJson,
        String accessTokenEnc,
        String refreshTokenEnc,
        Instant tokenExpiresAt,
        CrmIntegrationStatus status,
        Instant connectedAt,
        Instant createdAt
) {
}
