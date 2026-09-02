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
}
