package uz.murodjon.robotcallv2.alert.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.alert.application.port.output.AlertRuleRepository;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRuleFilter;
import uz.murodjon.robotcallv2.alert.infrastructure.persistence.entity.AlertRuleEntity;
import uz.murodjon.robotcallv2.alert.infrastructure.persistence.mapper.AlertRuleEntityMapper;
import uz.murodjon.robotcallv2.alert.infrastructure.persistence.repository.AlertRuleJpaRepository;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class AlertRuleRepositoryAdapter implements AlertRuleRepository {

    private final AlertRuleJpaRepository jpaRepository;
    private final AlertRuleEntityMapper mapper;

    public AlertRuleRepositoryAdapter(AlertRuleJpaRepository jpaRepository, AlertRuleEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public AlertRule create(AlertRule rule) {
        AlertRuleEntity entity = new AlertRuleEntity();
        entity.setCompanyId(rule.companyId());
        mapper.applySettings(entity, rule);
        return mapper.toAlertRule(jpaRepository.save(entity));
    }

    @Override
    public AlertRule update(long companyId, long id, AlertRule settings) {
        return jpaRepository.findByCompanyIdAndId(companyId, id)
                .map(entity -> {
                    mapper.applySettings(entity, settings);
                    return mapper.toAlertRule(jpaRepository.save(entity));
                })
                .orElse(null);
    }

    @Override
    public void deleteByCompanyIdAndId(long companyId, long id) {
        jpaRepository.deleteByCompanyIdAndId(companyId, id);
    }

    @Override
    public AlertRule findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByCompanyIdAndId(companyId, id).map(mapper::toAlertRule).orElse(null);
    }

    @Override
    public PageableData<AlertRule> findPageByCompanyId(long companyId, AlertRuleFilter filter) {
        Page<AlertRuleEntity> page = jpaRepository.findAll(specification(companyId, filter), filter.pageable());
        return PageableData.of(page.getContent().stream().map(mapper::toAlertRule).toList(),
                filter.pageable().getPageNumber(), filter.pageable().getPageSize(), page.getTotalElements());
    }

    @Override
    public long countByCompanyId(long companyId) {
        return jpaRepository.countByCompanyId(companyId);
    }

    @Override
    public List<AlertRule> findEnabled() {
        return jpaRepository.findEnabledWithFeature(Feature.ALERTING).stream().map(mapper::toAlertRule).toList();
    }

    @Override
    public void recordEvaluation(long id, Double value, Instant evaluatedAt, boolean triggered) {
        jpaRepository.recordEvaluation(id, value, evaluatedAt, triggered);
    }

    private static Specification<AlertRuleEntity> specification(long companyId, AlertRuleFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (filter.metric() != null) {
                predicates.add(cb.equal(root.get("metric"), filter.metric()));
            }
            if (filter.enabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), filter.enabled()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
