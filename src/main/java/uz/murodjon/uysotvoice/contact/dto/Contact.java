package uz.murodjon.uysotvoice.contact.dto;

import java.time.Instant;

/** One row of {@code GET/POST /api/contacts...} (§10.8). */
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
