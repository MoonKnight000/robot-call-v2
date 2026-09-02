package uz.murodjon.robotcallv2.voice.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.voice.application.dto.UpdateVoiceSettingsRequest;
import uz.murodjon.robotcallv2.voice.application.port.input.VoiceSettingsUseCase;
import uz.murodjon.robotcallv2.voice.domain.entity.VoiceSettings;

@RestController
public class VoiceSettingsControllerImpl implements VoiceSettingsController {

    private final VoiceSettingsUseCase voiceSettingsUseCase;

    public VoiceSettingsControllerImpl(VoiceSettingsUseCase voiceSettingsUseCase) {
        this.voiceSettingsUseCase = voiceSettingsUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<VoiceSettings>> get() {
        return ResponseEntity.ok(ResponseData.ok(voiceSettingsUseCase.find()));
    }

    @Override
    public ResponseEntity<ResponseData<VoiceSettings>> update(UpdateVoiceSettingsRequest r) {
        return ResponseEntity.ok(ResponseData.ok(voiceSettingsUseCase.update(r)));
    }
}
