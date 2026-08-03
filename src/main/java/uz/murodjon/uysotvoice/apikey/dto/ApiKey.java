package uz.murodjon.uysotvoice.apikey.dto;

import uz.murodjon.uysotvoice.user.enums.UserRole;

import java.time.Instant;

/** One row of {@code GET/POST /api/settings/api-keys} (§11 settings). Never carries the raw key. */
public record ApiKey(
        long id,
        long companyId,
        String name,
        String keyPrefix,
        UserRole role,
        Instant createdAt,
        Instant lastUsedAt,
        Instant revokedAt
) {
}
