package uz.murodjon.robotcallv2.tool.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.tool.application.port.output.ToolExecutionRepository;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecution;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecutionFilter;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecutionStats;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolExecutionStatus;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.ToolExecutionEntity;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.mapper.ToolExecutionEntityMapper;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.repository.ToolExecutionJpaRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ToolExecutionRepositoryAdapter implements ToolExecutionRepository {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionRepositoryAdapter.class);

    private final ToolExecutionJpaRepository jpaRepository;
    private final ToolExecutionEntityMapper mapper;

    public ToolExecutionRepositoryAdapter(ToolExecutionJpaRepository jpaRepository, ToolExecutionEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void record(ToolExecution execution) {
        try {
            jpaRepository.save(mapper.toToolExecutionEntity(execution));
        } catch (Exception e) {
            // This runs on a live call. Losing the log line is a smaller problem than
            // losing the turn it was describing.
            log.warn("Tool execution log failed for {}: {}", execution.toolName(), e.getMessage());
        }
    }

    @Override
    public int countFailedByCompanyIdAndCallAttemptId(long companyId, long callAttemptId) {
        return (int) jpaRepository.countByCompanyIdAndCallAttemptIdAndStatusNot(companyId, callAttemptId,
                ToolExecutionStatus.OK);
    }

    @Override
    public PageableData<ToolExecution> findPageByCompanyId(long companyId, ToolExecutionFilter filter) {
        Page<ToolExecutionEntity> page = jpaRepository.findAll(specification(companyId, filter), filter.pageable());
        return PageableData.of(page.getContent().stream().map(mapper::toToolExecution).toList(),
                filter.pageable().getPageNumber(), filter.pageable().getPageSize(), page.getTotalElements());
    }

    @Override
    public List<ToolExecutionStats> findStatsByCompanyIdSince(long companyId, Instant since) {
        return jpaRepository.summarizeByCompanyIdSince(companyId, since).stream()
                .map(mapper::toToolExecutionStats)
                .toList();
    }

    private static Specification<ToolExecutionEntity> specification(long companyId, ToolExecutionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (filter.toolId() != null) {
                predicates.add(cb.equal(root.get("toolId"), filter.toolId()));
            }
            if (filter.callAttemptId() != null) {
                predicates.add(cb.equal(root.get("callAttemptId"), filter.callAttemptId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
