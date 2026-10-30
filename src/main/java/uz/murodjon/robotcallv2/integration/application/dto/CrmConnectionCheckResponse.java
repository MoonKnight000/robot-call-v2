package uz.murodjon.robotcallv2.integration.application.dto;

import uz.murodjon.robotcallv2.integration.domain.enums.CrmIntegrationStatus;

import java.time.Instant;

/**
 * POST /api/settings/integrations/check answer: whether the saved address and credential
 * still work, and the status the connection now has.
 *
 * @param ok        whether the CRM accepted the credential
 * @param status    {@code CONNECTED} when it did, {@code ERROR} when it did not
 * @param reason    why it did not, in a few words ({@code HTTP 401}, {@code unreachable (...)}); {@code null} when {@code ok}
 * @param checkedAt when the check ran
 */
public record CrmConnectionCheckResponse(boolean ok, CrmIntegrationStatus status, String reason, Instant checkedAt) {
}
