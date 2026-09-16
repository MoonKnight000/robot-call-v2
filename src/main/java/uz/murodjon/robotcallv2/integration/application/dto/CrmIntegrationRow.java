package uz.murodjon.robotcallv2.integration.application.dto;

import uz.murodjon.robotcallv2.integration.domain.entity.CrmIntegration;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmIntegrationStatus;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/settings/integrations row (§11).
 *
 * @param accountHost the host of a connected amoCRM, Kommo or Bitrix24 account — never the
 *                    full address, which for Bitrix24 is the credential; {@code null} for
 *                    Uysot
 */
public record CrmIntegrationRow(
        long companyId,
        CrmProvider provider,
        String appName,
        List<CrmGrant> grants,
        CrmIntegrationStatus status,
        Instant connectedAt,
        String accountHost
) {
    public static CrmIntegrationRow of(CrmIntegration c, List<CrmGrant> grants, String accountHost) {
        return new CrmIntegrationRow(c.companyId(), c.provider(), c.appName(), grants, c.status(), c.connectedAt(),
                accountHost);
    }
}
