package uz.murodjon.robotcallv2.agent.tts;

import java.util.Map;

/**
 * Gemini Text-to-Speech settings ({@code voice-agent.tts.gemini.*}).
 * Uses {@code gemini-3.1-flash-tts-preview} to synthesize controllable, expressive speech
 * streaming chunk-by-chunk.
 *
 * @param apiKey                Gemini API key (defaults to GEMINI_API_KEY)
 * @param url                   Gemini REST/SSE endpoint base URL
 * @param model                 Gemini TTS model (default: gemini-3.1-flash-tts-preview)
 * @param voice                 default voice name (e.g. Aoede, Fenrir, Puck, Charon, Kore)
 * @param voices                optional per-language voice names (e.g. uz-UZ -> Aoede, ru-RU -> Kore)
 * @param sampleRate            audio sample rate (default: 24000 Hz)
 * @param connectTimeoutSeconds HTTP connection timeout
 */
public record GeminiTtsProperties(
        String apiKey,
        String url,
        String model,
        String voice,
        Map<String, String> voices,
        int sampleRate,
        int connectTimeoutSeconds
) {
    public GeminiTtsProperties {
        if (url == null || url.isBlank()) {
            url = "https://generativelanguage.googleapis.com/v1beta/models";
        }
        if (model == null || model.isBlank()) {
            model = "gemini-3.1-flash-tts-preview";
        }
        if (voice == null || voice.isBlank()) {
            voice = "Aoede";
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
