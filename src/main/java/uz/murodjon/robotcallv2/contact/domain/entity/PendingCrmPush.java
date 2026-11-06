package uz.murodjon.robotcallv2.contact.domain.entity;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An imported contact whose values still owe its CRM record a write.
 *
 * @param requestedAt  when the change was requested — finishing a push clears only this request,
 *                     so a change made while the push was on its way is pushed again
 * @param attempts     failed attempts so far
 * @param customFields the contact's values now
 */
public record PendingCrmPush(
        long companyId,
        long contactId,
        String provider,
        String externalId,
        Instant requestedAt,
        int attempts,
        Map<String, String> customFields
) {

    public PendingCrmPush {
        customFields = customFields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(customFields));
    }
}
