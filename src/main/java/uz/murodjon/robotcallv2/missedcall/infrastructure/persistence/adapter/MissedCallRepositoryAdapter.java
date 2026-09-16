package uz.murodjon.robotcallv2.missedcall.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.missedcall.infrastructure.persistence.mapper.MissedCallEntityMapper;
import uz.murodjon.robotcallv2.missedcall.application.port.output.MissedCallRepository;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCall;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallFilter;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallOccurrence;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallStatus;
import uz.murodjon.robotcallv2.missedcall.infrastructure.persistence.entity.MissedCallEntity;
import uz.murodjon.robotcallv2.missedcall.infrastructure.persistence.repository.MissedCallJpaRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class MissedCallRepositoryAdapter implements MissedCallRepository {

    private static final Set<MissedCallStatus> OPEN = EnumSet.of(MissedCallStatus.NEW, MissedCallStatus.IN_PROGRESS);

    private final MissedCallJpaRepository jpaRepository;
    private final MissedCallEntityMapper mapper;

    public MissedCallRepositoryAdapter(MissedCallJpaRepository jpaRepository, MissedCallEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, MissedCallOccurrence occurrence, Instant at) {
        MissedCallEntity entity = new MissedCallEntity();
        entity.setCompanyId(companyId);
        entity.setPhone(occurrence.phone());
        entity.setReason(occurrence.reason());
        entity.setStatus(MissedCallStatus.NEW);
        entity.setCallCount(1);
        entity.setFirstAt(at);
        entity.setLastAt(at);
        entity.setQueueId(occurrence.queueId());
        entity.setInboundRouteId(occurrence.inboundRouteId());
        entity.setLastCallAttemptId(occurrence.callAttemptId());
        entity.setVoicemailMessageId(occurrence.voicemailMessageId());
        return jpaRepository.saveAndFlush(entity).getId();
    }

    @Override
    public void touch(long id, MissedCallOccurrence occurrence, Instant at) {
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.setCallCount(entity.getCallCount() + 1);
            entity.setLastAt(at);
            entity.setReason(occurrence.reason());
            if (occurrence.callAttemptId() != null) {
                entity.setLastCallAttemptId(occurrence.callAttemptId());
            }
            if (occurrence.voicemailMessageId() != null) {
                entity.setVoicemailMessageId(occurrence.voicemailMessageId());
            }
            if (occurrence.queueId() != null) {
                entity.setQueueId(occurrence.queueId());
            }
            if (occurrence.inboundRouteId() != null) {
                entity.setInboundRouteId(occurrence.inboundRouteId());
            }
            jpaRepository.save(entity);
        });
    }

    @Override
    public MissedCall find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toMissedCall).orElse(null);
    }

    @Override
    public MissedCall findOpenByPhone(long companyId, String phone) {
        return jpaRepository.findByCompanyIdAndPhoneAndStatusIn(companyId, phone, OPEN).stream()
                .findFirst()
                .map(mapper::toMissedCall)
                .orElse(null);
    }

    @Override
    public void update(long companyId, long id, MissedCallStatus status, String note, Long resolvedByUserId,
                       Instant resolvedAt) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setStatus(status);
            entity.setNote(note);
            entity.setResolvedByUserId(resolvedByUserId);
            entity.setResolvedAt(resolvedAt);
            jpaRepository.save(entity);
        });
    }

    @Override
    public void assign(long companyId, long id, Long userId) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setAssignedUserId(userId);
            jpaRepository.save(entity);
        });
    }

    @Override
    public int resolveOpenByPhone(long companyId, String phone, MissedCallStatus status, Long userId, Instant at) {
        return jpaRepository.resolveOpen(companyId, phone, status, userId, at, OPEN);
    }

    @Override
    public void markCallbackAttempt(long id, Instant at) {
        jpaRepository.markCallbackAttempt(id, at);
    }

    @Override
    public boolean claimAutoCallback(long companyId, long id, Instant now, Instant retryBefore) {
        return jpaRepository.claimAutoCallback(id, companyId, now, retryBefore) == 1;
    }

    @Override
    public List<MissedCall> findAll(long companyId, MissedCallFilter filter) {
        return jpaRepository.findAll(matching(companyId, filter), filter.pageable()).stream()
                .map(mapper::toMissedCall)
                .toList();
    }

    @Override
    public long count(long companyId, MissedCallFilter filter) {
        return jpaRepository.count(matching(companyId, filter));
    }

    @Override
    public List<MissedCall> findAutoCallbackCandidates(Instant retryBefore, int maxAttempts, int limit) {
        return jpaRepository.findAutoCallbackCandidates(retryBefore, maxAttempts, limit).stream()
                .map(mapper::toMissedCall)
                .toList();
    }

    private static Specification<MissedCallEntity> matching(long companyId, MissedCallFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.reason() != null) {
                predicates.add(cb.equal(root.get("reason"), filter.reason()));
            }
            if (filter.assignedUserId() != null) {
                predicates.add(cb.equal(root.get("assignedUserId"), filter.assignedUserId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
