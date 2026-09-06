package uz.murodjon.robotcallv2.voice.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/tts/voices/{id}/preview} body — the line to hear the voice speak. */
public record PreviewVoiceRequest(
        @NotBlank @Size(max = 500) String text
) {
}
