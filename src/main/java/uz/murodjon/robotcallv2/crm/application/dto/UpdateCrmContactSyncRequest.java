package uz.murodjon.robotcallv2.crm.application.dto;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactFieldMapping;

import java.util.List;

/**
 * PUT /api/settings/crm/contact-sync — every value is replaced; a {@code null} list means none.
 *
 * @param fieldMappings CRM field → contact field, for the import
 * @param pushMappings  contact field → CRM field, written back to the CRM
 */
public record UpdateCrmContactSyncRequest(
        @NotNull Boolean enabled,
        List<CrmContactFieldMapping> fieldMappings,
        List<CrmContactFieldMapping> pushMappings
) {
}
