package uz.murodjon.robotcallv2.integration.application.dto;

import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationDefinition;
import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationAuthMethod;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;

import java.util.List;

/**
 * GET /api/platform/integrations row: one catalog entry as the platform admin edits it.
 *
 * @param provider     the entry's key
 * @param category     key of its category
 * @param categoryName the category's heading, for display
 * @param authMethods  how a company can connect it, the preferred one first
 * @param available    whether a connector exists in the code; false — companies see "coming soon"
 * @param builtIn      shipped with the platform: it cannot be deleted
 * @param iconFileId   the logo's stored file; send it back on update to keep the logo
 * @param iconUrl      where the logo is fetched ({@code GET} it with the usual auth header); null while it has none
 */
public record IntegrationDefinitionRow(
        IntegrationProvider provider,
        String name,
        String category,
        String categoryName,
        List<IntegrationAuthMethod> authMethods,
        boolean available,
        boolean builtIn,
        int sortOrder,
        Long iconFileId,
        String iconUrl
) {
    public static IntegrationDefinitionRow of(IntegrationDefinition definition, String categoryName) {
        return new IntegrationDefinitionRow(definition.provider(), definition.name(), definition.category(),
                categoryName, definition.authMethods(), definition.available(), definition.builtIn(),
                definition.sortOrder(), definition.iconFileId(), definition.iconUrl());
    }
}
