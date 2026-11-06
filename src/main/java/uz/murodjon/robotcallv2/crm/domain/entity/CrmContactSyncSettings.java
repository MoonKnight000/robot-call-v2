package uz.murodjon.robotcallv2.crm.domain.entity;

import java.time.Instant;
import java.util.List;

/**
 * What one company imports from its CRM's contacts, and what it writes back.
 *
 * @param enabled       whether the scheduled import runs
 * @param fieldMappings CRM fields copied into contact fields; name and phone are always imported
 * @param pushMappings  contact fields written back to the CRM record an imported contact came
 *                      from, when they change by hand or after a call; empty — nothing is written.
 *                      Independent of {@code enabled}
 */
public record CrmContactSyncSettings(
        long companyId,
        boolean enabled,
        List<CrmContactFieldMapping> fieldMappings,
        List<CrmContactFieldMapping> pushMappings,
        Instant updatedAt
) {

    public CrmContactSyncSettings {
        fieldMappings = fieldMappings == null ? List.of() : List.copyOf(fieldMappings);
        pushMappings = pushMappings == null ? List.of() : List.copyOf(pushMappings);
    }

    /** What a write carries: the adapter stamps the company and the time. */
    public static CrmContactSyncSettings values(boolean enabled, List<CrmContactFieldMapping> fieldMappings,
                                                List<CrmContactFieldMapping> pushMappings) {
        return new CrmContactSyncSettings(0, enabled, fieldMappings, pushMappings, null);
    }
}
