package uz.murodjon.uysotvoice.voice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.shared.api.ResponseData;
import uz.murodjon.uysotvoice.voice.dto.UpdateVoiceSettingsRequest;
import uz.murodjon.uysotvoice.voice.dto.VoiceSettings;
import uz.murodjon.uysotvoice.voice.service.VoiceSettingsService;

@RestController
public class VoiceSettingsControllerImpl implements VoiceSettingsController {

    private final VoiceSettingsService service;

    public VoiceSettingsControllerImpl(VoiceSettingsService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<VoiceSettings>> get() {
        return ResponseEntity.ok(ResponseData.ok(service.find()));
    }

    @Override
    public ResponseEntity<ResponseData<VoiceSettings>> update(UpdateVoiceSettingsRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.update(r)));
    }
}
