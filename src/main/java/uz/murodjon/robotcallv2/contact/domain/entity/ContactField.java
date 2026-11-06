package uz.murodjon.robotcallv2.contact.domain.entity;

import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldType;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldUpdateMode;

import java.time.Instant;
import java.util.List;

/**
 * One of the company's contact fields: the definition of a key in {@link Contact#customFields()}.
 *
 * @param key           the custom-field key, also the call variable name; fixed once created
 * @param label         what the contacts table shows as the column title
 * @param type          the kind of value; fixed once created
 * @param options       the allowed values of an {@link ContactFieldType#ENUM} field, empty otherwise
 * @param description   what the field captures — also the merge instruction of an ACCUMULATE field
 * @param extractionKey the post-call extraction field whose value fills this field, {@code null} = none
 * @param updateMode    how an extracted value meets the stored one
 */
public record ContactField(
        long id,
        String key,
        String label,
        ContactFieldType type,
        List<String> options,
        String description,
        String extractionKey,
        ContactFieldUpdateMode updateMode,
        Instant createdAt
) {

    public ContactField {
        options = options == null ? List.of() : List.copyOf(options);
        updateMode = updateMode == null ? ContactFieldUpdateMode.OVERWRITE : updateMode;
        extractionKey = extractionKey == null || extractionKey.isBlank() ? null : extractionKey.trim();
    }

    /** A field on its way to storage; identity and {@code createdAt} are stamped there. */
    public static ContactField of(String key, String label, ContactFieldType type, List<String> options,
                                  String description, String extractionKey, ContactFieldUpdateMode updateMode) {
        return new ContactField(0, key, label, type, options, description, extractionKey, updateMode, null);
    }
}
