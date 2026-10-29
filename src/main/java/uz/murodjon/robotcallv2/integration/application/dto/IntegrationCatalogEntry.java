package uz.murodjon.robotcallv2.integration.application.dto;

import uz.murodjon.robotcallv2.integration.domain.enums.CrmAuthMethod;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationCatalogStatus;
import uz.murodjon.robotcallv2.shared.integration.IntegrationCategory;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;

/**
 * One row of GET /api/settings/integrations/catalog, as seen by one company.
 *
 * @param authMethod how it connects; null while it is only {@code COMING_SOON}
 * @param available  whether it can be connected right now — {@code status == AVAILABLE}
 */
public record IntegrationCatalogEntry(
        IntegrationProvider provider,
        String displayName,
        IntegrationCategory category,
        CrmAuthMethod authMethod,
        IntegrationCatalogStatus status,
        boolean available
) {
}
