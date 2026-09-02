package uz.murodjon.robotcallv2.integration.application.dto;

import uz.murodjon.robotcallv2.integration.domain.enums.CrmAuthMethod;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;

/**
 * One row of GET /api/settings/integrations/catalog (report #10).
 */
public record CrmCatalogEntry(
        CrmProvider provider,
        String displayName,
        CrmAuthMethod authMethod,
        boolean available
) {
}
