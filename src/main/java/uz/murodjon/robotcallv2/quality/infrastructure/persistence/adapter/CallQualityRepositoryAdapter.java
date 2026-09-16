package uz.murodjon.robotcallv2.quality.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.quality.application.port.output.CallQualityRepository;
import uz.murodjon.robotcallv2.quality.domain.entity.CallQuality;
import uz.murodjon.robotcallv2.quality.domain.entity.CallReviewFilter;
import uz.murodjon.robotcallv2.quality.domain.entity.ScoredAgent;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity.CallQualityEntity;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.mapper.CallQualityEntityMapper;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.repository.CallQualityJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class CallQualityRepositoryAdapter implements CallQualityRepository {

    private final CallQualityJpaRepository jpaRepository;
    private final CallQualityEntityMapper mapper;

    public CallQualityRepositoryAdapter(CallQualityJpaRepository jpaRepository, CallQualityEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public CallQuality save(CallQuality quality) {
        return mapper.toCallQuality(jpaRepository.save(mapper.toCallQualityEntity(quality)));
    }

    @Override
    public CallQuality findByCompanyIdAndCallAttemptId(long companyId, long callAttemptId) {
        return jpaRepository.findByCompanyIdAndCallAttemptId(companyId, callAttemptId)
                .map(mapper::toCallQuality)
                .orElse(null);
    }

    @Override
    public PageableData<CallQuality> findPageByCompanyId(long companyId, CallReviewFilter filter) {
        Page<CallQualityEntity> page = jpaRepository.findAll(reviewSpecification(companyId, filter), filter.pageable());
        return PageableData.of(page.getContent().stream().map(mapper::toCallQuality).toList(),
                filter.pageable().getPageNumber(), filter.pageable().getPageSize(), page.getTotalElements());
    }

    @Override
    @Transactional
    public void markRepeatCalls(long companyId, Collection<Long> callAttemptIds) {
        jpaRepository.markRepeatCalls(companyId, callAttemptIds);
    }

    @Override
    public List<ScoredAgent> findAgentsScoredSince(Instant since) {
        return jpaRepository.countByAgentSince(since).stream()
                .map(row -> new ScoredAgent((Long) row[0], (Long) row[1], (Long) row[2]))
                .toList();
    }

    @Override
    public List<CallQuality> findByAgentSince(long companyId, long agentId, Instant since) {
        return jpaRepository
                .findByCompanyIdAndAiAgentIdAndCreatedAtGreaterThanEqual(companyId, agentId, since)
                .stream()
                .map(mapper::toCallQuality)
                .toList();
    }

    /** The company's reviewed or flagged calls — or only those in the filter's state — narrowed by agent and time. */
    private static Specification<CallQualityEntity> reviewSpecification(long companyId, CallReviewFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            predicates.add(filter.status() != null
                    ? cb.equal(root.get("reviewStatus"), filter.status())
                    : cb.isNotNull(root.get("reviewStatus")));
            if (filter.aiAgentId() != null) {
                predicates.add(cb.equal(root.get("aiAgentId"), filter.aiAgentId()));
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
