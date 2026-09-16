package uz.murodjon.robotcallv2.voicemail.application.port.output;

import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessage;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessageFilter;
import uz.murodjon.robotcallv2.voicemail.domain.enums.VoicemailStatus;

import java.util.List;

public interface VoicemailMessageRepository {

    long create(long companyId, VoicemailMessage message);

    void updateStatus(long companyId, long id, VoicemailStatus status);

    VoicemailMessage find(long companyId, long id);

    List<VoicemailMessage> findAll(long companyId, VoicemailMessageFilter filter);

    long count(long companyId, VoicemailMessageFilter filter);
}
