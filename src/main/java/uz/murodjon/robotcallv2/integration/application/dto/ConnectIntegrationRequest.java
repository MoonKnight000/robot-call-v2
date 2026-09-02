package uz.murodjon.robotcallv2.integration.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * PUT /api/settings/integrations/uysot body (§11, report #10).
 */
public record ConnectIntegrationRequest(
        @NotBlank String appName,
        @NotEmpty List<CrmGrant> grants
) {
}
