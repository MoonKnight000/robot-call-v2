package uz.murodjon.robotcallv2.voice.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/** {@code PUT /api/settings/voice} body — every field {@code null} clears that override. */
public record UpdateVoiceSettingsRequest(
        @DecimalMin("0.1") @DecimalMax("3.0") Double speed,
        @DecimalMin("-20.0") @DecimalMax("20.0") Double pitch
) {
}
