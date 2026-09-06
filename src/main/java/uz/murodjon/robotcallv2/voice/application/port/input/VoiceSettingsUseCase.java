package uz.murodjon.robotcallv2.voice.application.port.input;

import uz.murodjon.robotcallv2.voice.application.dto.UpdateVoiceSettingsRequest;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;
import uz.murodjon.robotcallv2.voice.domain.entity.VoiceSettings;

public interface VoiceSettingsUseCase {

    VoiceSettings findByCompanyId(long companyId);

    VoiceSettings updateByCompanyId(long companyId, UpdateVoiceSettingsRequest request);

    EffectiveVoiceSettings effective(long companyId);
}
