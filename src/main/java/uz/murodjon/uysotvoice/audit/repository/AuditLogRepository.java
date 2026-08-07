package uz.murodjon.uysotvoice.audit.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.audit.domain.AuditLog;
import uz.murodjon.uysotvoice.audit.entity.AuditLogEntity;

import java.time.Instant;
import java.util.List;

/**
 * JPA-backed DAO for {@code audit_log} (§11). Every method takes its {@code companyId}
 * explicitly; resolving "which company am I acting for" is the service's job, not this
 * class's.
 */
@Repository
public class AuditLogRepository {

    private final AuditLogJpaRepository jpaRepository;

    public AuditLogRepository(AuditLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /** {@code log}'s {@code id}/{@code companyId}/{@code createdAt} are ignored and re-stamped on save. */
    public AuditLog save(long companyId, AuditLog log) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setActor(log.actor());
        entity.setAction(log.action());
        entity.setEntity(log.entity());
        entity.setEntityId(log.entityId());
        entity.setDetail(log.detail());
        entity.setCreatedAt(Instant.now());
        entity.setCompanyId(companyId);
        entity.setIpAddress(log.ipAddress());
        return toAuditLog(jpaRepository.save(entity));
    }

    public List<AuditLog> findByCompanyId(long companyId, String actor, String action, String entity,
                                           Pageable pageable) {
        return jpaRepository.findByCompanyId(companyId, actor, action, entity, pageable).stream()
                .map(AuditLogRepository::toAuditLog)
                .toList();
    }

    public long countByCompanyId(long companyId, String actor, String action, String entity) {
        return jpaRepository.countByCompanyId(companyId, actor, action, entity);
    }

    private static AuditLog toAuditLog(AuditLogEntity e) {
        return new AuditLog(e.getId(), e.getCompanyId(), e.getActor(), e.getAction(), e.getEntity(), e.getEntityId(),
                e.getDetail(), e.getCreatedAt(), e.getIpAddress());
    }
}
