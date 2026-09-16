package uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBox;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessage;
import uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.entity.VoicemailBoxEntity;
import uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.entity.VoicemailMessageEntity;

@Component
public class VoicemailEntityMapper {

    public VoicemailBox toVoicemailBox(VoicemailBoxEntity entity) {
        return new VoicemailBox(entity.getId(), entity.getCompanyId(), entity.getName(), entity.getGreetingText(),
                entity.getGreetingFileId(), entity.getMaxSeconds(), entity.isTranscribe(), entity.isEnabled(),
                entity.getCreatedAt());
    }

    public VoicemailMessage toVoicemailMessage(VoicemailMessageEntity entity) {
        return new VoicemailMessage(entity.getId(), entity.getCompanyId(), entity.getBoxId(), entity.getCallAttemptId(),
                entity.getCallerPhone(), entity.getStoredFileId(), entity.getDurationSec(), entity.getTranscript(),
                entity.getStatus(), entity.getCreatedAt());
    }
}
