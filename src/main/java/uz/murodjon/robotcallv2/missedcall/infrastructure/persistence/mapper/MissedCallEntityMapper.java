package uz.murodjon.robotcallv2.missedcall.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCall;
import uz.murodjon.robotcallv2.missedcall.infrastructure.persistence.entity.MissedCallEntity;

@Component
public class MissedCallEntityMapper {

    public MissedCall toMissedCall(MissedCallEntity entity) {
        return new MissedCall(entity.getId(), entity.getCompanyId(), entity.getPhone(), entity.getReason(),
                entity.getStatus(), entity.getCallCount(), entity.getFirstAt(), entity.getLastAt(),
                entity.getQueueId(), entity.getInboundRouteId(), entity.getLastCallAttemptId(),
                entity.getVoicemailMessageId(), entity.getAssignedUserId(), entity.getCallbackAttempts(),
                entity.getLastCallbackAt(), entity.getResolvedAt(), entity.getResolvedByUserId(), entity.getNote());
    }
}
