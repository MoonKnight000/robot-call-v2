package uz.murodjon.robotcallv2.apikey.domain.entity;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.time.Instant;
import java.util.Set;

/**
 * A credential a company's own system authenticates with, instead of a person's login.
 *
 * <p>The secret half is never here. {@code keyHash} is a SHA-256 of the whole key and
 * {@code keyPrefix} is the readable part shown in the console; the key itself exists once,
 * in the response that created it. A database somebody walks off with therefore hands them
 * nothing they can call this API with.
 *
 * @param id         storage's, null before it is written
 * @param companyId  the tenant every request made with this key acts as
 * @param name       what a person calls it in the console
 * @param keyPrefix  the readable half, unique across the platform
 * @param keyHash    SHA-256 of the whole key
 * @param scopes     what it was granted; what it may actually do is this intersected with
 *                   {@link uz.murodjon.robotcallv2.apikey.domain.service.ApiKeyScopes}
 * @param createdBy  the user who issued it, or null once that user is deleted
 * @param lastUsedAt when it last authenticated a request, or null if it never has
 * @param expiresAt  when it stops working, or null for a key with no end date
 * @param revokedAt  when it was switched off, or null while it still works
 * @param createdAt  storage's to stamp
 */
public record ApiKey(
        Long id,
        long companyId,
        String name,
        String keyPrefix,
        String keyHash,
        Set<Permission> scopes,
        Long createdBy,
        Instant lastUsedAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt
) {
    /** A key being issued: everything storage stamps is left for storage. */
    public static ApiKey issued(long companyId, String name, String keyPrefix, String keyHash,
                                Set<Permission> scopes, Long createdBy, Instant expiresAt) {
        return new ApiKey(null, companyId, name, keyPrefix, keyHash, scopes, createdBy,
                null, expiresAt, null, null);
    }

    /**
     * Whether this key may still authenticate a request.
     *
     * <p>Asked on every request rather than enforced by a query, so that a key revoked a
     * second ago stops working on the next call and not on the next cache refresh.
     */
    public boolean isUsableAt(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }
}
