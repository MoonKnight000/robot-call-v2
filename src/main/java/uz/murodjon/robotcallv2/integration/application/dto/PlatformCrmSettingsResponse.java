package uz.murodjon.robotcallv2.integration.application.dto;

import java.util.List;

public record PlatformCrmSettingsResponse(
        String appName,
        List<CrmGrant> grants
) {
}
