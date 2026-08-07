package uz.murodjon.uysotvoice.audit.domain;

import java.time.Instant;

/**
 * One row of {@code audit_log} — the domain model passed between {@code AuditService} and
 * {@code AuditLogRepository}, and returned by {@code POST /api/reports/audit/list} (§11).
 *
 * <p>{@code id}, {@code companyId} and {@code createdAt} are repository-owned: on the way
 * in ({@link uz.murodjon.uysotvoice.audit.repository.AuditLogRepository#save}) they are
 * ignored and re-stamped; on the way out they are always filled. Use {@link #entry} to
 * build a value that is only meant to be written.
 *
 * @param actor     authenticated principal that made the request ({@code system} for the
 *                  dialer's own scheduled work)
 * @param action    short code, e.g. {@code CAMPAIGN_START}
 * @param entity    what was acted on ({@code campaign}, {@code target}, {@code call})
 * @param entityId  that entity's id as text
 * @param detail    free-text context
 * @param createdAt when it happened
 * @param ipAddress the caller's remote address, or {@code null} when the action ran
 *                  outside an HTTP request (the dialer's own scheduled work)
 */
public record AuditLog(
        long id,
        long companyId,
        String actor,
        String action,
        String entity,
        String entityId,
        String detail,
        Instant createdAt,
        String ipAddress
) {

    /** The entry alone, without the repository-owned {@code id}/{@code companyId}/{@code createdAt}. */
    public static AuditLog entry(String actor, String action, String entity, String entityId, String detail,
                                  String ipAddress) {
        return new AuditLog(0L, 0L, actor, action, entity, entityId, detail, null, ipAddress);
    }
}
