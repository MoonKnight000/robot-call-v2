package uz.murodjon.uysotvoice.voice.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.shared.api.ResponseData;
import uz.murodjon.uysotvoice.voice.dto.UpdateVoiceSettingsRequest;
import uz.murodjon.uysotvoice.voice.dto.VoiceSettings;

/**
 * Per-company TTS tuning overrides (§11 settings) — scoped to the caller's own company
 * via {@code CurrentCompany} (JWT), not a path id. ADMIN-only ({@code SecurityConfig}).
 */
@RequestMapping("/api/settings/voice")
public interface VoiceSettingsController {

    /** {@code null} fields mean "using the process default" — never {@code 404}. */
    @GetMapping
    ResponseEntity<ResponseData<VoiceSettings>> get();

    @PutMapping
    ResponseEntity<ResponseData<VoiceSettings>> update(@Valid @RequestBody UpdateVoiceSettingsRequest r);
}
