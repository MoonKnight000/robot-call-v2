package uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.voicemail.application.port.output.VoicemailMessageRepository;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessage;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessageFilter;
import uz.murodjon.robotcallv2.voicemail.domain.enums.VoicemailStatus;
import uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.entity.VoicemailMessageEntity;
import uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.mapper.VoicemailEntityMapper;
import uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.repository.VoicemailMessageJpaRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class VoicemailMessageRepositoryAdapter implements VoicemailMessageRepository {

    private final VoicemailMessageJpaRepository jpaRepository;
    private final VoicemailEntityMapper mapper;

    public VoicemailMessageRepositoryAdapter(VoicemailMessageJpaRepository jpaRepository, VoicemailEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, VoicemailMessage message) {
        VoicemailMessageEntity entity = new VoicemailMessageEntity();
        entity.setCompanyId(companyId);
        entity.setBoxId(message.boxId());
        entity.setCallAttemptId(message.callAttemptId());
        entity.setCallerPhone(message.callerPhone());
        entity.setStoredFileId(message.storedFileId());
        entity.setDurationSec(message.durationSec());
        entity.setTranscript(message.transcript());
        entity.setStatus(message.status() != null ? message.status() : VoicemailStatus.NEW);
        entity.setCreatedAt(Instant.now());
        return jpaRepository.save(entity).getId();
    }

    @Override
    public void updateStatus(long companyId, long id, VoicemailStatus status) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setStatus(status);
            jpaRepository.save(entity);
        });
    }

    @Override
    public VoicemailMessage find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toVoicemailMessage).orElse(null);
    }

    @Override
    public List<VoicemailMessage> findAll(long companyId, VoicemailMessageFilter filter) {
        return jpaRepository.findAll(matching(companyId, filter), filter.pageable()).stream()
                .map(mapper::toVoicemailMessage)
                .toList();
    }

    @Override
    public long count(long companyId, VoicemailMessageFilter filter) {
        return jpaRepository.count(matching(companyId, filter));
    }

    private static Specification<VoicemailMessageEntity> matching(long companyId, VoicemailMessageFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.boxId() != null) {
                predicates.add(cb.equal(root.get("boxId"), filter.boxId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
