package uz.murodjon.uysotvoice.integration.dto;

import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.integration.enums.CrmPermission;
import uz.murodjon.uysotvoice.integration.enums.CrmScopeLevel;

/**
 * One {@code {"permission": "...", "scope": "..."}} entry of Uysot's OAuth {@code
 * grants} param (report #10) — the company chooses which of these to request via
 * {@code ConnectIntegrationRequest.grants}; {@code CrmIntegrationService} base64-encodes
 * the list as JSON when building the authorize URL.
 */
public record CrmGrant(
        @NotNull CrmPermission permission,
        @NotNull CrmScopeLevel scope
) {
}
