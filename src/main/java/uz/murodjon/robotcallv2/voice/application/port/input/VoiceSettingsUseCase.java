package uz.murodjon.robotcallv2.voice.application.port.input;

import uz.murodjon.robotcallv2.voice.application.dto.UpdateVoiceSettingsRequest;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;
import uz.murodjon.robotcallv2.voice.domain.entity.VoiceSettings;

public interface VoiceSettingsUseCase {

    VoiceSettings find();

    VoiceSettings update(UpdateVoiceSettingsRequest r);

    EffectiveVoiceSettings effective(long companyId);
}
