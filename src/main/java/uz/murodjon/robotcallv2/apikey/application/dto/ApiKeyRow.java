package uz.murodjon.robotcallv2.apikey.application.dto;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.time.Instant;
import java.util.Set;

/**
 * One key as the console lists it. Carries no secret — {@code keyPrefix} is only enough
 * to tell one key from another.
 */
public record ApiKeyRow(
        long id,
        String name,
        String keyPrefix,
        Set<Permission> scopes,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt
) {
}
