package uz.murodjon.robotcallv2.audit.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.audit.infrastructure.persistence.entity.AuditLogEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;

@Component
public class AuditLogMapper {

    public AuditLog entityToDomain(AuditLogEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AuditLog(
                entity.getId(),
                entity.getCompanyId(),
                entity.getActor(),
                entity.getAction(),
                entity.getEntity(),
                entity.getEntityId(),
                entity.getDetail(),
                entity.getCreatedAt(),
                entity.getIpAddress()
        );
    }

    public AuditLogEntity domainToEntity(AuditLog domain, CompanyEntity company) {
        if (domain == null) {
            return null;
        }
        AuditLogEntity entity = new AuditLogEntity();
        entity.setActor(domain.actor());
        entity.setAction(domain.action());
        entity.setEntity(domain.entity());
        entity.setEntityId(domain.entityId());
        entity.setDetail(domain.detail());
        entity.setCreatedAt(domain.createdAt() != null ? domain.createdAt() : Instant.now());
        entity.setCompany(company);
        entity.setIpAddress(domain.ipAddress());
        return entity;
    }
}
