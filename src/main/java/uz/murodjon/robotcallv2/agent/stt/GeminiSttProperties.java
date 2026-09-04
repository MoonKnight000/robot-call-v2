package uz.murodjon.robotcallv2.agent.stt;

/**
 * Gemini Speech-to-Text streaming settings ({@code voice-agent.stt.gemini.*}).
 * Uses {@code gemini-3.5-transcribe} (or {@code gemini-3.5-transcribe-live}) over Gemini Bidi WebSocket.
 *
 * @param apiKey                Gemini API key (defaults to GEMINI_API_KEY)
 * @param url                   WebSocket URL for BidiGenerateContent
 * @param model                 Gemini STT model (default: gemini-3.5-transcribe)
 * @param sampleRate            audio sample rate (default: 16000 Hz)
 * @param connectTimeoutSeconds WebSocket connect timeout in seconds
 */
public record GeminiSttProperties(
        String apiKey,
        String url,
        String model,
        int sampleRate,
        int connectTimeoutSeconds
) {
    public GeminiSttProperties {
        if (url == null || url.isBlank()) {
            url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";
        }
        if (model == null || model.isBlank()) {
            model = "gemini-3.5-transcribe";
        }
        if (sampleRate <= 0) {
            sampleRate = 16000;
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
    }
}
