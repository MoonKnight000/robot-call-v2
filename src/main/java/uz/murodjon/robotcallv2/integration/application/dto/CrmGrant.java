package uz.murodjon.robotcallv2.integration.application.dto;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmPermission;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmScopeLevel;

/**
 * One entry of Uysot's OAuth grants param (report #10).
 */
public record CrmGrant(
        @NotNull CrmPermission permission,
        @NotNull CrmScopeLevel scope
) {
}
