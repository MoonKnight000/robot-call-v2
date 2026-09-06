package uz.murodjon.robotcallv2.contact.domain.entity;

import java.time.Instant;

/**
 * Domain record for a contact record (1:1 with contact table, §10.8).
 */
public record Contact(
        long id,
        String name,
        String phone,
        String address,
        String tags,
        String notes,
        Instant createdAt
) {

    /** A contact on its way to storage; identity and {@code createdAt} are stamped there. */
    public static Contact of(String name, String phone, String address, String tags, String notes) {
        return new Contact(0, name, phone, address, tags, notes, null);
    }

    /** The editable fields of an existing contact — the phone is not one of them. */
    public static Contact profile(String name, String address, String tags, String notes) {
        return new Contact(0, name, null, address, tags, notes, null);
    }
}
