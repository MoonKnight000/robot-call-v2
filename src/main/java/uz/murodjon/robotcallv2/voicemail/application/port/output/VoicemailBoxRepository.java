package uz.murodjon.robotcallv2.voicemail.application.port.output;

import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBox;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBoxFilter;

import java.util.List;

public interface VoicemailBoxRepository {

    long create(long companyId, VoicemailBox box);

    void update(long companyId, long id, VoicemailBox box);

    void delete(long companyId, long id);

    VoicemailBox find(long companyId, long id);

    List<VoicemailBox> findAll(long companyId, VoicemailBoxFilter filter);

    long count(long companyId);
}
