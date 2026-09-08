package uz.murodjon.robotcallv2.agent.tts;

import java.util.Map;

/**
 * OpenAI Text-to-Speech settings ({@code voice-agent.tts.open-ai.*}).
 *
 * @param apiKey                OpenAI API key (defaults to OPENAI_API_KEY)
 * @param url                   speech endpoint
 * @param model                 TTS model (default: gpt-4o-mini-tts)
 * @param voice                 default voice name (alloy, ash, ballad, coral, echo, fable,
 *                              nova, onyx, sage, shimmer, verse, marin, cedar)
 * @param voices                optional per-language voice names (uz-UZ -> ..., ru-RU -> ...)
 * @param instructions          free-text delivery direction this model accepts — accent,
 *                              tone, pace. Blank leaves the voice as it is
 * @param sampleRate            rate the {@code pcm} response format is returned at; OpenAI
 *                              documents 24 kHz and does not let the caller choose, so this
 *                              exists to be corrected if that ever changes, not to be tuned
 * @param connectTimeoutSeconds HTTP connection timeout
 */
public record OpenAiTtsProperties(
        String apiKey,
        String url,
        String model,
        String voice,
        Map<String, String> voices,
        String instructions,
        int sampleRate,
        int connectTimeoutSeconds
) {
    public OpenAiTtsProperties {
        if (url == null || url.isBlank()) {
            url = "https://api.openai.com/v1/audio/speech";
        }
        if (model == null || model.isBlank()) {
            model = "gpt-4o-mini-tts";
        }
        if (voice == null || voice.isBlank()) {
            voice = "alloy";
        }
        if (voices == null) {
            voices = Map.of();
        }
        if (sampleRate <= 0) {
            sampleRate = 24000;
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
    }
}
