package uz.murodjon.robotcallv2.callrecord.application.mapper;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;

public final class CallAttemptMapper {

    private CallAttemptMapper() {
    }

    public static CallAttempt toDomain(CallAttemptEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CallAttempt(
                entity.getId(),
                entity.getTarget() != null ? entity.getTarget().getId() : 0L,
                entity.getSipCallId(),
                entity.getAsteriskChannel(),
                entity.getLanguage(),
                entity.getStartedAt(),
                entity.getAnsweredAt(),
                entity.getEndedAt(),
                entity.getDurationSec(),
                entity.getDisposition(),
                entity.getHangupCause(),
                entity.getRecordingFileId(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getFinalizeAttempts(),
                entity.getCompanyId(),
                entity.getInboundRouteId(),
                entity.getOperatorUserId()
        );
    }
}
