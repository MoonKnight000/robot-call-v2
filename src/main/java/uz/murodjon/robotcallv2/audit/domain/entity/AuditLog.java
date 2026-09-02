package uz.murodjon.robotcallv2.audit.domain.entity;

import java.time.Instant;

/**
 * Domain model for an audit log record.
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
    public static AuditLog entry(String actor, String action, String entity, String entityId, String detail,
                                  String ipAddress) {
        return new AuditLog(0L, 0L, actor, action, entity, entityId, detail, null, ipAddress);
    }
}
