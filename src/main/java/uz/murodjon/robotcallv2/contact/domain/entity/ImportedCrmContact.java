package uz.murodjon.robotcallv2.contact.domain.entity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One CRM record on its way into the contacts.
 *
 * @param provider     the CRM product's name
 * @param externalId   the record's id in that CRM
 * @param name         the record's name; blank leaves the contact's name as it is
 * @param phone        the number the contact is matched and filed under
 * @param customFields mapped CRM values by contact field key; a blank value never clears one
 */
public record ImportedCrmContact(
        String provider,
        String externalId,
        String name,
        String phone,
        Map<String, String> customFields
) {

    public ImportedCrmContact {
        customFields = customFields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(customFields));
    }
}
