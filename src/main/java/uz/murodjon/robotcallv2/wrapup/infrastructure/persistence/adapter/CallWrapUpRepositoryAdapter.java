package uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.wrapup.application.port.output.CallWrapUpRepository;
import uz.murodjon.robotcallv2.wrapup.domain.entity.CallWrapUp;
import uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.entity.CallWrapUpEntity;
import uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.mapper.CallWrapUpEntityMapper;
import uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.repository.CallWrapUpJpaRepository;

import java.time.Instant;

@Component
public class CallWrapUpRepositoryAdapter implements CallWrapUpRepository {

    private final CallWrapUpJpaRepository jpaRepository;
    private final CallWrapUpEntityMapper mapper;

    public CallWrapUpRepositoryAdapter(CallWrapUpJpaRepository jpaRepository, CallWrapUpEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /** {@code created_at} is re-stamped on a correction: it is when the result was last given. */
    @Override
    @Transactional
    public CallWrapUp upsert(long companyId, CallWrapUp wrapUp) {
        CallWrapUpEntity entity = jpaRepository.findById(wrapUp.callId()).orElseGet(() -> {
            CallWrapUpEntity fresh = new CallWrapUpEntity();
            fresh.setCallAttemptId(wrapUp.callId());
            fresh.setCompanyId(companyId);
            return fresh;
        });
        mapper.applyEditableFields(entity, wrapUp);
        entity.setCreatedAt(Instant.now());
        return mapper.toCallWrapUp(jpaRepository.save(entity));
    }
}
