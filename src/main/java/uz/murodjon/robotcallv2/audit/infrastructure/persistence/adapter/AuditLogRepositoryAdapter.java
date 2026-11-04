package uz.murodjon.robotcallv2.audit.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.audit.application.port.output.AuditLogRepository;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditFacets;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.audit.infrastructure.persistence.entity.AuditLogEntity;
import uz.murodjon.robotcallv2.audit.infrastructure.persistence.mapper.AuditLogEntityMapper;
import uz.murodjon.robotcallv2.audit.infrastructure.persistence.repository.AuditLogJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class AuditLogRepositoryAdapter implements AuditLogRepository {

    private final AuditLogJpaRepository auditLogJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final AuditLogEntityMapper mapper;

    /** More distinct values than this is not a dropdown any more. */
    private static final int FACET_LIMIT = 500;

    public AuditLogRepositoryAdapter(AuditLogJpaRepository auditLogJpaRepository,
                                     CompanyJpaRepository companyJpaRepository,
                                     AuditLogEntityMapper mapper) {
        this.auditLogJpaRepository = auditLogJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public AuditLog save(long companyId, AuditLog log) {
        CompanyEntity comp = companyJpaRepository.getReferenceById(companyId);

        AuditLogEntity entity = new AuditLogEntity();
        entity.setCompany(comp);
        entity.setActor(truncate(log.actor(), 255));
        entity.setAction(truncate(log.action(), 100));
        entity.setEntity(truncate(log.entity(), 64));
        entity.setEntityId(truncate(log.entityId(), 255));
        entity.setDetail(log.detail());
        entity.setCreatedAt(Instant.now());
        entity.setIpAddress(truncate(log.ipAddress(), 45));
        return mapper.toAuditLog(auditLogJpaRepository.save(entity));
    }

    @Override
    public List<AuditLog> findByCompanyId(long companyId, AuditFilter filter) {
        Specification<AuditLogEntity> spec = buildSpecification(companyId, filter);
        return auditLogJpaRepository.findAll(spec, filter.pageable()).stream()
                .map(mapper::toAuditLog)
                .toList();
    }

    @Override
    public long countByCompanyId(long companyId, AuditFilter filter) {
        Specification<AuditLogEntity> spec = buildSpecification(companyId, filter);
        return auditLogJpaRepository.count(spec);
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff, int batchSize) {
        return auditLogJpaRepository.deleteCreatedBefore(cutoff, batchSize);
    }

    private static Specification<AuditLogEntity> buildSpecification(long companyId, AuditFilter filter) {
        String actor = filter.actor();
        String action = filter.action();
        String entity = filter.entity();
        List<String> actionPrefixes = filter.actionPrefixes() == null ? List.of()
                : filter.actionPrefixes().stream().filter(p -> p != null && !p.isBlank()).limit(20).toList();
        String entityId = filter.entityId();
        String q = filter.q() == null ? null : filter.q().trim().toLowerCase();
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
            if (!actionPrefixes.isEmpty()) {
                predicates.add(cb.or(actionPrefixes.stream()
                        .map(prefix -> cb.like(root.get("action"), escapeLike(prefix) + "%", '\\'))
                        .toArray(Predicate[]::new)));
            }
            if (entityId != null && !entityId.isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), entityId.trim()));
            }
            if (q != null && !q.isEmpty()) {
                String pattern = "%" + escapeLike(q) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("detail")), pattern, '\\'),
                        cb.like(cb.lower(root.get("entityId")), pattern, '\\'),
                        cb.like(cb.lower(root.get("actor")), pattern, '\\')));
            }
            if (filter.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.dateTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    public AuditFacets findFacets(long companyId) {
        return new AuditFacets(
                auditLogJpaRepository.findDistinctActors(companyId, FACET_LIMIT),
                auditLogJpaRepository.findDistinctActions(companyId, FACET_LIMIT),
                auditLogJpaRepository.findDistinctEntities(companyId, FACET_LIMIT));
    }

    /** A user's text in a LIKE pattern matches literally: % and _ are not wildcards. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String truncate(String val, int maxLen) {
        if (val == null || val.length() <= maxLen) {
            return val;
        }
        return val.substring(0, maxLen);
    }
}
