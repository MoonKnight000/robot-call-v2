package uz.murodjon.robotcallv2.integration.domain.entity;

import uz.murodjon.robotcallv2.integration.domain.enums.CrmIntegrationStatus;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;

import java.time.Instant;

/**
 * Domain model of crm_integration (§11 settings).
 *
 * @param baseUrlEnc where an amoCRM, Kommo or Bitrix24 connection lives — the account
 *                   address or the inbound webhook URL — encrypted like the tokens;
 *                   {@code null} for Uysot, whose address is platform configuration
 */
public record CrmIntegration(
        long companyId,
        CrmProvider provider,
        String appName,
        String grantsJson,
        String accessTokenEnc,
        String refreshTokenEnc,
        String baseUrlEnc,
        Instant tokenExpiresAt,
        CrmIntegrationStatus status,
        Instant connectedAt,
        Instant createdAt
) {

    /**
     * What an amoCRM, Kommo or Bitrix24 connection is saved with: connected as soon as it
     * is saved, since there is no consent step to wait for. The adapter stamps the company
     * and the times.
     */
    public static CrmIntegration credentials(CrmProvider provider, String baseUrlEnc, String accessTokenEnc) {
        return new CrmIntegration(0, provider, null, null, accessTokenEnc, null, baseUrlEnc, null,
                CrmIntegrationStatus.CONNECTED, null, null);
    }
}
