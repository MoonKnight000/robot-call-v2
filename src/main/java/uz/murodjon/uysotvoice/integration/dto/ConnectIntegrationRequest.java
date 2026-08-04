package uz.murodjon.uysotvoice.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * {@code PUT /api/settings/integrations/uysot} body (§11, report #10) — declares this
 * company's own Uysot OAuth app identity ahead of the consent redirect.
 *
 * @param appName shown to the user on Uysot's consent screen ({@code app_name} param)
 * @param grants  which permissions to request; must stay within what the platform's
 *                own Uysot app was registered for, or Uysot rejects with {@code
 *                invalid_scope} at consent time
 */
public record ConnectIntegrationRequest(
        @NotBlank String appName,
        @NotEmpty List<CrmGrant> grants
) {
}
