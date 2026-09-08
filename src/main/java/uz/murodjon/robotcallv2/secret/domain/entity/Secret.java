package uz.murodjon.robotcallv2.secret.domain.entity;

import java.time.Instant;

/**
 * Domain entity representing a secured credential or environment secret.
 * The {@code value} stored in the database is encrypted via AES-256-GCM.
 */
public record Secret(
        Long id,
        Long companyId,
        String key,
        String value,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
