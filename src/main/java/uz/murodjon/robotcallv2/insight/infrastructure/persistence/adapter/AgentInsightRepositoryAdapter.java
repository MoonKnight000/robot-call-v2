package uz.murodjon.robotcallv2.insight.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.insight.application.port.output.AgentInsightRepository;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsight;
import uz.murodjon.robotcallv2.insight.domain.entity.AgentInsightFilter;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightStatus;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightType;
import uz.murodjon.robotcallv2.insight.infrastructure.persistence.entity.AgentInsightEntity;
import uz.murodjon.robotcallv2.insight.infrastructure.persistence.mapper.AgentInsightEntityMapper;
import uz.murodjon.robotcallv2.insight.infrastructure.persistence.repository.AgentInsightJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.ArrayList;
import java.util.List;

@Component
public class AgentInsightRepositoryAdapter implements AgentInsightRepository {

    private final AgentInsightJpaRepository jpaRepository;
    private final AgentInsightEntityMapper mapper;

    public AgentInsightRepositoryAdapter(AgentInsightJpaRepository jpaRepository,
                                         AgentInsightEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public AgentInsight save(AgentInsight insight) {
        AgentInsightEntity entity = insight.id() == null
                ? new AgentInsightEntity()
                : jpaRepository.findById(insight.id()).orElseGet(AgentInsightEntity::new);
        return mapper.toAgentInsight(jpaRepository.save(mapper.toAgentInsightEntity(insight, entity)));
    }

    @Override
    public AgentInsight findByCompanyIdAndId(long companyId, long insightId) {
        return jpaRepository.findByCompanyIdAndId(companyId, insightId)
                .map(mapper::toAgentInsight)
                .orElse(null);
    }

    @Override
    public PageableData<AgentInsight> findPageByCompanyId(long companyId, AgentInsightFilter filter) {
        Page<AgentInsightEntity> page = jpaRepository.findAll(specification(companyId, filter), filter.pageable());
        return PageableData.of(page.getContent().stream().map(mapper::toAgentInsight).toList(),
                filter.pageable().getPageNumber(), filter.pageable().getPageSize(), page.getTotalElements());
    }

    @Override
    public boolean existsProposed(long aiAgentId, InsightType type, String title) {
        return jpaRepository.existsByAiAgentIdAndTypeAndTitleAndStatus(
                aiAgentId, type, title, InsightStatus.PROPOSED);
    }

    @Override
    public AgentInsight findByEvalRunId(long evalRunId) {
        return jpaRepository.findByEvalRunId(evalRunId).map(mapper::toAgentInsight).orElse(null);
    }

    private static Specification<AgentInsightEntity> specification(long companyId, AgentInsightFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (filter.aiAgentId() != null) {
                predicates.add(cb.equal(root.get("aiAgentId"), filter.aiAgentId()));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
