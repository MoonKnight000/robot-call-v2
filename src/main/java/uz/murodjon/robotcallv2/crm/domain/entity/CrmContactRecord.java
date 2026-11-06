package uz.murodjon.robotcallv2.crm.domain.entity;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One contact as the CRM lists it.
 *
 * @param externalId the CRM's id of the record
 * @param phones     its numbers as the CRM spells them, first the main one
 * @param fields     the requested fields' values as text, by CRM field id; a field without a value is absent
 * @param modifiedAt when the CRM last changed the record; {@code null} when it does not say
 */
public record CrmContactRecord(
        String externalId,
        String name,
        List<String> phones,
        Map<String, String> fields,
        Instant modifiedAt
) {

    public CrmContactRecord {
        phones = phones == null ? List.of() : List.copyOf(phones);
        fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
