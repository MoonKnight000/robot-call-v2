package uz.murodjon.uysotvoice.integration.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code PUT /api/settings/integrations/uysot} body (§11) — the company's own Uysot OAuth app credentials. */
public record ConnectIntegrationRequest(
        @NotBlank String clientId,
        @NotBlank String clientSecret
) {
}
