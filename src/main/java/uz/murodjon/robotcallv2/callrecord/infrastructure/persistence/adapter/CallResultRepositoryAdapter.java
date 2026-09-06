package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.application.port.output.CallResultRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallResult;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallResultJpaRepository;

@Component
public class CallResultRepositoryAdapter implements CallResultRepository {

    private final CallResultJpaRepository jpaRepository;

    public CallResultRepositoryAdapter(CallResultJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void insertIgnoringConflict(CallResult result) {
        jpaRepository.insertIgnoringConflict(
                result.callId(),
                result.summary(),
                result.reasonCode() == null ? null : result.reasonCode().name(),
                result.promisedDate(),
                result.promisedAmount(),
                result.sentiment() == null ? null : result.sentiment().name(),
                result.needsFollowUp(),
                result.followUpNote(),
                result.escalated(),
                result.crmNoteId(),
                result.outcome());
    }
}
