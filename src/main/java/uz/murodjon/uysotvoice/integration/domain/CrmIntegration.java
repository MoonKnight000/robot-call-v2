package uz.murodjon.uysotvoice.integration.domain;

import uz.murodjon.uysotvoice.integration.enums.CrmIntegrationStatus;
import uz.murodjon.uysotvoice.integration.enums.CrmProvider;

import java.time.Instant;

/**
 * The domain model of {@code crm_integration} (§11 settings) — one company's Uysot CRM
 * OAuth connection, passed between {@code CrmIntegrationService} and {@link
 * uz.murodjon.uysotvoice.integration.repository.CrmIntegrationRepository}. Carries the
 * encrypted tokens, so it never reaches a controller directly; API responses go through
 * {@link uz.murodjon.uysotvoice.integration.dto.CrmIntegrationRow}.
 *
 * @param accessTokenEnc  {@code shared.util.SecretCipher} ciphertext, never plaintext
 * @param refreshTokenEnc {@code shared.util.SecretCipher} ciphertext, never plaintext
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
