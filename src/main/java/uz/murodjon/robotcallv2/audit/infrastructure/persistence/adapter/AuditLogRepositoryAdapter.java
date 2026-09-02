package uz.murodjon.robotcallv2.audit.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.audit.application.mapper.AuditLogMapper;
import uz.murodjon.robotcallv2.audit.application.port.output.AuditLogRepository;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.audit.infrastructure.persistence.entity.AuditLogEntity;
import uz.murodjon.robotcallv2.audit.infrastructure.persistence.repository.AuditLogJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class AuditLogRepositoryAdapter implements AuditLogRepository {

    private final AuditLogJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final AuditLogMapper mapper;

    public AuditLogRepositoryAdapter(AuditLogJpaRepository jpaRepository,
                                     CompanyJpaRepository companyJpaRepository,
                                     AuditLogMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public AuditLog save(long companyId, AuditLog log) {
        CompanyEntity comp = companyJpaRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId));

        AuditLogEntity entity = new AuditLogEntity();
        entity.setCompany(comp);
        entity.setActor(truncate(log.actor(), 255));
        entity.setAction(truncate(log.action(), 100));
        entity.setEntity(truncate(log.entity(), 64));
        entity.setEntityId(truncate(log.entityId(), 255));
        entity.setDetail(log.detail());
        entity.setCreatedAt(Instant.now());
        entity.setIpAddress(truncate(log.ipAddress(), 45));
        return mapper.entityToDomain(jpaRepository.save(entity));
    }

    @Override
    public List<AuditLog> findByCompanyId(long companyId, String actor, String action, String entity,
                                           Pageable pageable) {
        Specification<AuditLogEntity> spec = buildSpecification(companyId, actor, action, entity);
        return jpaRepository.findAll(spec, pageable).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public long countByCompanyId(long companyId, String actor, String action, String entity) {
        Specification<AuditLogEntity> spec = buildSpecification(companyId, actor, action, entity);
        return jpaRepository.count(spec);
    }

    private static Specification<AuditLogEntity> buildSpecification(long companyId, String actor, String action, String entity) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));

            if (actor != null && !actor.isBlank()) {
                predicates.add(cb.equal(root.get("actor"), actor));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (entity != null && !entity.isBlank()) {
                predicates.add(cb.equal(root.get("entity"), entity));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String truncate(String val, int maxLen) {
        if (val == null || val.length() <= maxLen) {
            return val;
        }
        return val.substring(0, maxLen);
    }
}
