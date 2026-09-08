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
 */
public record OpenAiSttProperties(
        String apiKey,
        String url,
        String model,
        int sampleRate,
        int connectTimeoutSeconds
) {
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
    }
}
