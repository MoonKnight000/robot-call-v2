package uz.murodjon.robotcallv2.voicemail.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of POST/PUT /api/voicemail-boxes.
 *
 * @param maxSeconds null is 120
 * @param transcribe null is true
 * @param enabled    null is true
 */
public record SaveVoicemailBoxRequest(
        @NotBlank String name,
        String greetingText,
        Long greetingFileId,
        Integer maxSeconds,
        Boolean transcribe,
        Boolean enabled
) {
}
