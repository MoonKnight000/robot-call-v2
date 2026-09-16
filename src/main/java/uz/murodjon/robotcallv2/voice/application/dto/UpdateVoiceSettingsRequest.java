package uz.murodjon.robotcallv2.voice.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * {@code PUT /api/settings/voice} body — every field {@code null} clears that override.
 * {@code speed} is range-checked by {@code VoiceSettingsValidator}, the same check an
 * agent's speed gets, so both answer {@code VOICE_SPEED_OUT_OF_RANGE} on both sides.
 */
public record UpdateVoiceSettingsRequest(
        Double speed,
        @DecimalMin("-20.0") @DecimalMax("20.0") Double pitch
) {
}
