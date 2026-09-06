package uz.murodjon.robotcallv2.voice.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;
import uz.murodjon.robotcallv2.voice.application.dto.UpdateVoiceSettingsRequest;
import uz.murodjon.robotcallv2.voice.domain.entity.VoiceSettings;

/**
 * Per-company TTS tuning overrides (§11 settings).
 */
@RequestMapping("/api/settings/voice")
public interface VoiceSettingsController {

    @PreAuthorize("hasAuthority('VOICE_READ')")
    @GetMapping
    ResponseEntity<ResponseData<VoiceSettings>> get(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('VOICE_EDIT')")
    @PutMapping
    ResponseEntity<ResponseData<VoiceSettings>> update(@CurrentCompanyId long companyId,
                                                        @Valid @RequestBody UpdateVoiceSettingsRequest request);
}
