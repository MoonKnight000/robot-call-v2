package uz.murodjon.robotcallv2.voice.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.voice.application.dto.UpdateVoiceSettingsRequest;
import uz.murodjon.robotcallv2.voice.domain.entity.VoiceSettings;

/**
 * Per-company TTS tuning overrides (§11 settings).
 */
@RequestMapping("/api/settings/voice")
public interface VoiceSettingsController {

    @GetMapping
    ResponseEntity<ResponseData<VoiceSettings>> get();

    @PutMapping
    ResponseEntity<ResponseData<VoiceSettings>> update(@Valid @RequestBody UpdateVoiceSettingsRequest r);
}
