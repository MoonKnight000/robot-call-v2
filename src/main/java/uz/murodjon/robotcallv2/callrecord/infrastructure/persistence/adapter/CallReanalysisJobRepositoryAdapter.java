package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallReanalysisJobRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisFilter;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallReanalysisJob;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallReanalysisStatus;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallReanalysisJobEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.mapper.CallReanalysisJobEntityMapper;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallReanalysisJobJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class CallReanalysisJobRepositoryAdapter implements CallReanalysisJobRepository {

    private static final Set<CallReanalysisStatus> ACTIVE =
            Set.of(CallReanalysisStatus.QUEUED, CallReanalysisStatus.RUNNING);

    private final CallReanalysisJobJpaRepository jpaRepository;
    private final CallReanalysisJobEntityMapper mapper;

    public CallReanalysisJobRepositoryAdapter(CallReanalysisJobJpaRepository jpaRepository,
                                              CallReanalysisJobEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public CallReanalysisJob create(CallReanalysisJob job) {
        return mapper.toCallReanalysisJob(jpaRepository.save(mapper.toCallReanalysisJobEntity(job)));
    }

    @Override
    public CallReanalysisJob findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByCompanyIdAndId(companyId, id).map(mapper::toCallReanalysisJob).orElse(null);
    }

    @Override
    public PageableData<CallReanalysisJob> findPageByCompanyId(long companyId, CallReanalysisFilter filter) {
        Page<CallReanalysisJobEntity> page = jpaRepository.findAll(specification(companyId, filter), filter.pageable());
        return PageableData.of(page.getContent().stream().map(mapper::toCallReanalysisJob).toList(),
                filter.pageable().getPageNumber(), filter.pageable().getPageSize(), page.getTotalElements());
    }

    @Override
    public boolean existsActiveByCompanyId(long companyId) {
        return jpaRepository.existsByCompanyIdAndStatusIn(companyId, ACTIVE);
    }

    @Override
    public CallReanalysisJob findNextActive() {
        return jpaRepository.findFirstByStatusInOrderById(ACTIVE).map(mapper::toCallReanalysisJob).orElse(null);
    }

    @Override
    public boolean updateProgress(CallReanalysisJob job) {
        return jpaRepository.updateProgress(job.id(), ACTIVE, job.status(), job.processedCalls(), job.updatedCalls(),
                job.failedCalls(), job.promptTokens(), job.completionTokens(), job.chargedUzs(), job.lastCallId(),
                job.lastError(), job.startedAt(), job.finishedAt()) == 1;
    }

    @Override
    public boolean cancel(long companyId, long id, Instant finishedAt) {
        return jpaRepository.cancel(companyId, id, ACTIVE, CallReanalysisStatus.CANCELLED, finishedAt) == 1;
    }

    private static Specification<CallReanalysisJobEntity> specification(long companyId, CallReanalysisFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (filter.aiAgentId() != null) {
                predicates.add(cb.equal(root.get("aiAgentId"), filter.aiAgentId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
