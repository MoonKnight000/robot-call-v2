package uz.murodjon.robotcallv2.agent.realtime;

/**
 * Pipecat Cloud & Anthropic Claude Realtime engine properties.
 * Bound from {@code voice-agent.realtime.pipecat.*}.
 *
 * @param apiKey                 Pipecat Cloud API key / Bearer token (or Daily public key)
 * @param url                    Pipecat Cloud public REST endpoint for launching agent sessions
 * @param wsUrl                  Direct WebSocket URL for raw bidirectional audio streaming (if bridged)
 * @param agentName              Agent identifier deployed on Pipecat Cloud (e.g. phone-agent)
 * @param model                  Underlying LLM used in Pipecat pipeline (e.g. claude-3-5-haiku-20241022)
 * @param sampleRate             PCM audio sample rate in Hz (default 16000)
 * @param connectTimeoutSeconds  Connection timeout in seconds
 */
public record PipecatRealtimeProperties(
        String apiKey,
        String url,
        String wsUrl,
        String agentName,
        String model,
        int sampleRate,
        int connectTimeoutSeconds
) {
    public PipecatRealtimeProperties {
        if (url == null || url.isBlank()) {
            url = "https://api.pipecat.daily.co/v1/public";
        }
        if (agentName == null || agentName.isBlank()) {
            agentName = "phone-agent";
        }
        if (model == null || model.isBlank()) {
            model = "claude-3-5-haiku-20241022";
        }
        if (sampleRate <= 0) {
            sampleRate = 16000;
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 15;
        }
    }
}
