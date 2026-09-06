package uz.murodjon.robotcallv2.agent.stt;

/**
 * Gemini Speech-to-Text streaming settings ({@code voice-agent.stt.gemini.*}).
 * Uses {@code gemini-3.5-transcribe-live} over the Gemini Live (Bidi) WebSocket.
 *
 * <p>The model has to be one that supports {@code bidiGenerateContent}. Plain
 * {@code gemini-3.5-transcribe} is the batch ({@code generateContent}) model and the
 * socket closes on it at setup with "not found ... or is not supported for
 * bidiGenerateContent" — which is what every Gemini-STT call did until 2026-09-05.
 *
 * @param apiKey                Gemini API key (defaults to GEMINI_API_KEY)
 * @param url                   WebSocket URL for BidiGenerateContent
 * @param model                 Gemini STT model (default: gemini-3.5-transcribe-live)
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
            model = "gemini-3.5-transcribe-live";
        }
        if (sampleRate <= 0) {
            sampleRate = 16000;
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
    }
}
