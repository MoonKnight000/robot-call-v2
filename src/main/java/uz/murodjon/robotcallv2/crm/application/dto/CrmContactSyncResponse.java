package uz.murodjon.robotcallv2.crm.application.dto;

import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactFieldMapping;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncState;

import java.util.List;

/**
 * GET /api/settings/crm/contact-sync.
 *
 * @param provider  the company's connected CRM, {@code null} when it has none
 * @param supported whether that CRM's contacts can be imported and written back
 */
public record CrmContactSyncResponse(
        boolean enabled,
        String provider,
        boolean supported,
        List<CrmContactFieldMapping> fieldMappings,
        List<CrmContactFieldMapping> pushMappings,
        CrmContactSyncState state
) {
}
