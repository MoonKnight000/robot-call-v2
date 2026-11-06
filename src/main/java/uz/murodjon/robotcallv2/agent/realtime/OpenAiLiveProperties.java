package uz.murodjon.robotcallv2.agent.realtime;

/**
 * Settings for OpenAI GPT-Live ({@code /v1/live/sessions}) — a full-duplex voice model that
 * hands reasoning and tool calls to a separate Responses model (delegation).
 *
 * @param apiKey                OpenAI API key
 * @param url                   WebSocket endpoint (default: wss://api.openai.com/v1/live/sessions)
 * @param model                 the voice model (default: gpt-live-1)
 * @param voice                 voice name (default: marin); fixed for the whole session
 * @param connectTimeoutSeconds WebSocket connect and {@code session.started} timeout
 * @param audioFormat           {@code pcmu} — G.711 µ-law at 8 kHz, what the phone line already
 *                              carries, no resampling — or {@code pcm} (16-bit PCM at 16 kHz)
 * @param delegationModel       the Responses model the voice model delegates reasoning and tool
 *                              calls to; how hard it thinks is the agent's {@code reasoningEffort}
 */
public record OpenAiLiveProperties(
        String apiKey,
        String url,
        String model,
        String voice,
        int connectTimeoutSeconds,
        String audioFormat,
        String delegationModel
) {
    public OpenAiLiveProperties {
        if (url == null || url.isBlank()) {
            url = "wss://api.openai.com/v1/live/sessions";
        }
        if (model == null || model.isBlank()) {
            model = "gpt-live-1";
        }
        if (voice == null || voice.isBlank()) {
            voice = "marin";
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
        if (audioFormat == null || audioFormat.isBlank()) {
            audioFormat = "pcmu";
        }
        if (delegationModel == null || delegationModel.isBlank()) {
            delegationModel = "gpt-6-luna";
        }
    }

    /** Whether the session speaks µ-law at the telephone rate rather than 16 kHz PCM. */
    public boolean telephoneAudio() {
        return "pcmu".equalsIgnoreCase(audioFormat);
    }
}
