package uz.murodjon.robotcallv2.voice.application.port.output;

import uz.murodjon.robotcallv2.voice.domain.entity.VoiceSettings;

public interface VoiceSettingsRepository {

    VoiceSettings findByCompanyId(long companyId);

    VoiceSettings upsert(long companyId, Double speed, Double pitch);
}
