package uz.murodjon.robotcallv2.voice.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The form fields of {@code POST /api/tts/voices/clone}; the recordings come as {@code files}.
 *
 * @param name     the voice's name in the agent form ("Dilnoza — call centre")
 * @param language BCP-47 language the voice will speak ({@code uz-UZ}, {@code ru-RU})
 * @param consent  the speaker agreed to their voice being cloned — required to be true
 */
public record CloneVoiceRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$") String language,
        Boolean consent
) {
}
