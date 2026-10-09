package uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.wrapup.domain.entity.CallWrapUp;
import uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.entity.CallWrapUpEntity;

@Component
public class CallWrapUpEntityMapper {

    public CallWrapUp toCallWrapUp(CallWrapUpEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CallWrapUp(entity.getCallAttemptId(), entity.getCompanyId(), entity.getWrapUpCodeId(),
                entity.getCodeName(), entity.getOutcome(), entity.getOperatorUserId(), entity.getNote(),
                entity.getCallbackAt(), entity.getCreatedAt());
    }

    /** Everything a repeated wrap-up of the same call replaces. */
    public void applyEditableFields(CallWrapUpEntity entity, CallWrapUp wrapUp) {
        entity.setWrapUpCodeId(wrapUp.wrapUpCodeId());
        entity.setCodeName(wrapUp.codeName());
        entity.setOutcome(wrapUp.outcome());
        entity.setOperatorUserId(wrapUp.operatorUserId());
        entity.setNote(wrapUp.note());
        entity.setCallbackAt(wrapUp.callbackAt());
    }
}
