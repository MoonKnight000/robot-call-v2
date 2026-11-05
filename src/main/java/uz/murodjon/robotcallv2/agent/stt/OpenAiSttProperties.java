package uz.murodjon.robotcallv2.agent.stt;

/**
 * OpenAI realtime transcription settings ({@code voice-agent.stt.open-ai.*}).
 *
 * @param apiKey                OpenAI API key (defaults to OPENAI_API_KEY)
 * @param url                   realtime WebSocket endpoint, transcription intent included
 * @param model                 {@code gpt-live-transcribe} streams deltas as the caller
 *                              speaks; {@code gpt-transcribe} transcribes after the commit
 *                              and detects the language, at the cost of the interims this
 *                              pipeline speculates on
 * @param sampleRate            rate the audio is sent at. OpenAI documents transcription
 *                              sessions at 24 kHz, and the bridge upsamples the 8 kHz wire
 *                              audio to whatever is set here
 * @param connectTimeoutSeconds WebSocket connect and setup timeout
 * @param delay                 {@code audio.input.transcription.delay} for {@code gpt-live-transcribe}:
 *                              {@code minimal | low | medium | high | xhigh} — lower answers earlier,
 *                              higher transcribes more accurately. Blank (or anything else) leaves
 *                              OpenAI's own default; the milliseconds behind each level are not published
 */
public record OpenAiSttProperties(
        String apiKey,
        String url,
        String model,
        int sampleRate,
        int connectTimeoutSeconds,
        String delay
) {
    private static final java.util.Set<String> DELAYS = java.util.Set.of("minimal", "low", "medium", "high", "xhigh");

    public OpenAiSttProperties {
        if (url == null || url.isBlank()) {
            url = "wss://api.openai.com/v1/realtime?intent=transcription";
        }
        if (model == null || model.isBlank()) {
            model = "gpt-live-transcribe";
        }
        if (sampleRate <= 0) {
            sampleRate = 24000;
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
        delay = delay != null && DELAYS.contains(delay.trim().toLowerCase(java.util.Locale.ROOT))
                ? delay.trim().toLowerCase(java.util.Locale.ROOT) : null;
    }
}
