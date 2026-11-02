package uz.murodjon.robotcallv2.integration.application.dto;

import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationAuthMethod;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationCatalogStatus;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;

import java.util.List;

/**
 * One row of GET /api/settings/integrations/catalog, as seen by one company.
 *
 * @param category     key of the heading it is listed under
 * @param categoryName that heading's name
 * @param authMethod   the preferred way to connect — the first of {@code authMethods}
 * @param authMethods  every way it can connect
 * @param available    whether it can be connected right now — {@code status == AVAILABLE}
 * @param iconUrl      where the logo the platform admin uploaded is fetched; null while it has none
 */
public record IntegrationCatalogEntry(
        IntegrationProvider provider,
        String displayName,
        String category,
        String categoryName,
        IntegrationAuthMethod authMethod,
        List<IntegrationAuthMethod> authMethods,
        IntegrationCatalogStatus status,
        boolean available,
        String iconUrl
) {
}
