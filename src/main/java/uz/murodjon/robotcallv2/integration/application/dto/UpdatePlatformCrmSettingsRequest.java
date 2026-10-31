package uz.murodjon.robotcallv2.integration.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdatePlatformCrmSettingsRequest(
        @NotBlank String appName,
        @NotEmpty List<CrmGrant> grants
) {
}
