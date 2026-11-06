package uz.murodjon.robotcallv2.crm.domain.service;

import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactFieldMapping;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure rules for a CRM contact sync's field mappings. */
public final class CrmContactSyncValidator {

    /** As many as a contact can hold. */
    public static final int MAX_MAPPINGS = 50;
    private static final int MAX_CRM_FIELD_LENGTH = 128;
    /** Bitrix24's and amoCRM's phone field; never written back. */
    private static final String PHONE_FIELD = "PHONE";

    private CrmContactSyncValidator() {
    }

    /**
     * At most {@link #MAX_MAPPINGS}, each naming a CRM field and one of {@code contactFieldKeys},
     * and no contact field filled twice.
     */
    public static void validateMappings(List<CrmContactFieldMapping> mappings, Set<String> contactFieldKeys) {
        if (mappings.size() > MAX_MAPPINGS) {
            throw new ValidationException(ErrorCode.CRM_CONTACT_SYNC_TOO_MANY_MAPPINGS, MAX_MAPPINGS);
        }
        Set<String> seen = new HashSet<>();
        for (CrmContactFieldMapping mapping : mappings) {
            String crmField = mapping.crmField();
            if (crmField == null || crmField.isBlank() || crmField.length() > MAX_CRM_FIELD_LENGTH) {
                throw new ValidationException(ErrorCode.CRM_CONTACT_SYNC_CRM_FIELD_INVALID, crmField);
            }
            String contactField = mapping.contactField();
            if (contactField == null || !contactFieldKeys.contains(contactField)) {
                throw new ValidationException(ErrorCode.CRM_CONTACT_SYNC_CONTACT_FIELD_UNKNOWN, contactField);
            }
            if (!seen.add(contactField)) {
                throw new ValidationException(ErrorCode.CRM_CONTACT_SYNC_CONTACT_FIELD_TWICE, contactField);
            }
        }
    }

    /**
     * Write-back mappings: at most {@link #MAX_MAPPINGS}, each from one of {@code contactFieldKeys}
     * to a CRM field, no CRM field written twice, and never the phone — the number the contact is
     * matched by is the CRM's, not ours.
     */
    public static void validatePushMappings(List<CrmContactFieldMapping> mappings, Set<String> contactFieldKeys) {
        if (mappings.size() > MAX_MAPPINGS) {
            throw new ValidationException(ErrorCode.CRM_CONTACT_SYNC_TOO_MANY_MAPPINGS, MAX_MAPPINGS);
        }
        Set<String> seen = new HashSet<>();
        for (CrmContactFieldMapping mapping : mappings) {
            String crmField = mapping.crmField();
            if (crmField == null || crmField.isBlank() || crmField.length() > MAX_CRM_FIELD_LENGTH) {
                throw new ValidationException(ErrorCode.CRM_CONTACT_SYNC_CRM_FIELD_INVALID, crmField);
            }
            if (PHONE_FIELD.equalsIgnoreCase(crmField.strip())) {
                throw new ValidationException(ErrorCode.CRM_CONTACT_SYNC_PHONE_NOT_WRITABLE);
            }
            if (mapping.contactField() == null || !contactFieldKeys.contains(mapping.contactField())) {
                throw new ValidationException(ErrorCode.CRM_CONTACT_SYNC_CONTACT_FIELD_UNKNOWN, mapping.contactField());
            }
            if (!seen.add(crmField)) {
                throw new ValidationException(ErrorCode.CRM_CONTACT_SYNC_CRM_FIELD_TWICE, crmField);
            }
        }
    }
}
