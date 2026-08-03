package uz.murodjon.uysotvoice.audit.dto;

import java.time.Instant;

/**
 * One row of {@code audit_log}.
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
        String actor,
        String action,
        String entity,
        String entityId,
        String detail,
        Instant createdAt,
        String ipAddress
) {
}
