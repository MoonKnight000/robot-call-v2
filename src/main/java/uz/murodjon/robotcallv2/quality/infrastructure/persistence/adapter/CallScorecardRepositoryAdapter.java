package uz.murodjon.robotcallv2.quality.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.quality.application.port.output.CallScorecardRepository;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecard;
import uz.murodjon.robotcallv2.quality.domain.entity.CallScorecardFilter;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity.CallScorecardEntity;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.mapper.CallScorecardEntityMapper;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.repository.CallScorecardJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class CallScorecardRepositoryAdapter implements CallScorecardRepository {

    private final CallScorecardJpaRepository jpaRepository;
    private final CallScorecardEntityMapper mapper;

    public CallScorecardRepositoryAdapter(CallScorecardJpaRepository jpaRepository, CallScorecardEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public CallScorecard upsert(long companyId, CallScorecard scorecard) {
        CallScorecardEntity entity = jpaRepository.findById(scorecard.callId()).orElseGet(() -> {
            CallScorecardEntity created = new CallScorecardEntity();
            created.setCallId(scorecard.callId());
            created.setCompanyId(companyId);
            return created;
        });
        entity.setCreatedAt(Instant.now());
        mapper.applyEditableFields(entity, scorecard);
        return mapper.toCallScorecard(jpaRepository.save(entity));
    }

    @Override
    @Transactional
    public CallScorecard updateReview(long companyId, CallScorecard scorecard) {
        CallScorecardEntity entity = jpaRepository.findByCallIdAndCompanyId(scorecard.callId(), companyId)
                .orElse(null);
        if (entity == null) {
            return null;
        }
        mapper.applyReview(entity, scorecard);
        return mapper.toCallScorecard(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public CallScorecard findByCompanyIdAndCallId(long companyId, long callId) {
        return jpaRepository.findByCallIdAndCompanyId(callId, companyId).map(mapper::toCallScorecard).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageableData<CallScorecard> findPageByCompanyId(long companyId, CallScorecardFilter filter) {
        Page<CallScorecardEntity> page = jpaRepository.findAll(specification(companyId, filter), filter.pageable());
        return PageableData.of(page.getContent().stream().map(mapper::toCallScorecard).toList(),
                filter.pageable().getPageNumber(), filter.pageable().getPageSize(), page.getTotalElements());
    }

    @Override
    @Transactional
    public int deleteEvidenceWithoutTranscript(int batchSize) {
        return jpaRepository.clearEvidenceWithoutTranscript(batchSize);
    }

    /** The company's scored calls — or only those in the filter's review state — narrowed by operator and time. */
    private static Specification<CallScorecardEntity> specification(long companyId, CallScorecardFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("reviewStatus"), filter.status()));
            }
            if (filter.operatorUserId() != null) {
                predicates.add(cb.equal(root.get("operatorUserId"), filter.operatorUserId()));
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
