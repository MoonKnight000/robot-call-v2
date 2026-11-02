package uz.murodjon.robotcallv2.integration.domain.entity;

import uz.murodjon.robotcallv2.integration.domain.enums.IntegrationAuthMethod;
import uz.murodjon.robotcallv2.shared.integration.IntegrationProvider;

import java.util.List;

/**
 * One entry of the integration catalog, as the platform admin keeps it.
 *
 * @param key         stable key, UPPER_SNAKE_CASE; what a company's access switch and its connection name
 * @param name        the name companies see
 * @param category    key of the {@link IntegrationCategory} it is listed under
 * @param authMethods how a company can connect it, the preferred one first (Uysot: OAuth or a token;
 *                    Eskiz: login and password; Google Drive: Google's OAuth)
 * @param available   whether a connector for it exists in the code; false lists it as "coming soon".
 *                    Only the platform's own releases set it, never the admin
 * @param sortOrder   position among the entries, ascending
 * @param builtIn     shipped with the platform: it can be renamed and moved but not deleted
 * @param iconFileId  the {@code stored_file} holding its logo; null while it has none
 */
public record IntegrationDefinition(
        String key,
        String name,
        String category,
        List<IntegrationAuthMethod> authMethods,
        boolean available,
        int sortOrder,
        boolean builtIn,
        Long iconFileId
) {

    public IntegrationDefinition {
        authMethods = authMethods == null ? List.of() : List.copyOf(authMethods);
    }

    /**
     * Where a browser fetches the logo, or null while it has none. The version query changes with the
     * file, so a replaced logo is not served from a cache.
     */
    public String iconUrl() {
        return iconFileId == null ? null : "/api/integration-icons/" + key + "?v=" + iconFileId;
    }

    public IntegrationProvider provider() {
        return IntegrationProvider.valueOf(key);
    }

    /** The preferred way to connect, or null when none is declared. */
    public IntegrationAuthMethod primaryAuthMethod() {
        return authMethods.isEmpty() ? null : authMethods.getFirst();
    }


    /** A new, planned entry the admin added: no connector yet, not built in. */
    public static IntegrationDefinition planned(String key, String name, String category,
                                                List<IntegrationAuthMethod> authMethods, int sortOrder, Long iconFileId) {
        return new IntegrationDefinition(key, name, category, authMethods, false, sortOrder, false, iconFileId);
    }

    /** The same entry with the fields the admin may change replaced. */
    public IntegrationDefinition edited(String newName, String newCategory, List<IntegrationAuthMethod> newAuthMethods, Long newIconFileId,
                                        int newSortOrder) {
        return new IntegrationDefinition(key, newName, newCategory, newAuthMethods, available, newSortOrder, builtIn, newIconFileId);
    }
}
