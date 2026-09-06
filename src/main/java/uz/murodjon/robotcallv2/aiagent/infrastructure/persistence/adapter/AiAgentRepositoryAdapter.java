package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.aiagent.application.mapper.AiAgentMapper;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiAgentRepositoryAdapter implements AiAgentRepository {

    private final AiAgentJpaRepository jpaRepository;
    private final AiAgentMapper mapper;

    public AiAgentRepositoryAdapter(AiAgentJpaRepository jpaRepository, AiAgentMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public long create(AiAgent agent) {
        AiAgentEntity entity = new AiAgentEntity();
        entity.setCompanyId(agent.companyId());
        entity.setScenarioId(agent.scenarioId());
        entity.setCreatedBy(agent.createdBy());
        entity.setCreatedAt(Instant.now());
        mapper.applyEditableFields(entity, agent);
        return jpaRepository.save(entity).getId();
    }

    @Override
    @Transactional
    public void update(long companyId, long id, AiAgent agent) {
        AiAgentEntity entity = jpaRepository.findByIdAndCompanyId(id, companyId).orElse(null);
        if (entity == null) {
            return;
        }
        // The scenario is editable here and identity is not: an agent that reads a different
        // script is the same agent with a new script, but it never changes company or id.
        entity.setScenarioId(agent.scenarioId());
        mapper.applyEditableFields(entity, agent);
        jpaRepository.save(entity);
    }

    @Override
    public AiAgent findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId)
                .map(mapper::toAiAgent)
                .orElse(null);
    }

    @Override
    public List<AiAgent> findAll(long companyId, AiAgentFilter filter) {
        return jpaRepository.findAll(buildSpecification(companyId, filter), filter.pageable()).stream()
                .map(mapper::toAiAgent)
                .toList();
    }

    @Override
    public long count(long companyId, AiAgentFilter filter) {
        return jpaRepository.count(buildSpecification(companyId, filter));
    }

    @Override
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        for (AiAgentEntity entity : jpaRepository.findByIdIn(ids)) {
            names.put(entity.getId(), entity.getName());
        }
        return names;
    }

    @Override
    @Transactional
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    @Override
    public long countCampaignsUsing(long id) {
        return jpaRepository.countCampaignsUsing(id);
    }

    @Override
    public long countInboundRoutesUsing(long id) {
        return jpaRepository.countInboundRoutesUsing(id);
    }

    private static Specification<AiAgentEntity> buildSpecification(long companyId, AiAgentFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (filter.scenarioId() != null) {
                predicates.add(cb.equal(root.get("scenarioId"), filter.scenarioId()));
            }
            if (filter.enabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), filter.enabled()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + filter.search().toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
