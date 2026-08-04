package uz.murodjon.uysotvoice.integration.dto;

import uz.murodjon.uysotvoice.integration.enums.CrmAuthMethod;
import uz.murodjon.uysotvoice.integration.enums.CrmProvider;

/**
 * One row of {@code GET /api/settings/integrations/catalog} (report #10) — "which CRMs
 * can this company connect to." Static, not company-scoped.
 *
 * @param available {@code true} only for {@link CrmProvider#UYSOT} today — the others
 *                  are listed so the panel can show them as "coming soon" rather than
 *                  the company having no idea the platform is headed there; connecting
 *                  to one that is not available yet is rejected with a clear error
 */
public record CrmCatalogEntry(
        CrmProvider provider,
        String displayName,
        CrmAuthMethod authMethod,
        boolean available
) {
}
