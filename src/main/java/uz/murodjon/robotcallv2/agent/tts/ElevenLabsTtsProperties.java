package uz.murodjon.robotcallv2.agent.tts;

import java.util.Map;

/**
 * ElevenLabs Text-to-Speech settings ({@code voice-agent.tts.eleven-labs.*}).
 *
 * @param apiKey                ElevenLabs API key (defaults to ELEVENLABS_API_KEY)
 * @param url                   API base URL; the voice id and {@code /stream} are appended
 * @param model                 TTS model (default: eleven_v4_turbo — the realtime one with Uzbek)
 * @param voice                 default voice id (ElevenLabs ids are opaque, not names)
 * @param voices                optional per-language voice ids (uz-UZ -> ..., ru-RU -> ...)
 * @param connectTimeoutSeconds HTTP connection timeout
 */
public record ElevenLabsTtsProperties(
        String apiKey,
        String url,
        String model,
        String voice,
        Map<String, String> voices,
        int connectTimeoutSeconds
) {
    public ElevenLabsTtsProperties {
        if (url == null || url.isBlank()) {
            url = "https://api.elevenlabs.io/v1/text-to-speech";
        }
        if (model == null || model.isBlank()) {
            model = "eleven_v4_turbo";
        }
        if (voices == null) {
            voices = Map.of();
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
    }
}
