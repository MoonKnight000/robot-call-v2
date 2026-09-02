package uz.murodjon.robotcallv2.agent.realtime;

/**
 * Settings for Kyutai Moshi Realtime Speech-to-Speech Engine.
 *
 * @param apiKey               Optional API key / Token for Moshi server
 * @param url                  WebSocket endpoint (default: ws://localhost:8998/api/chat)
 * @param model                Model name (e.g. moshi, moshi-v1)
 * @param sampleRate           PCM Audio sample rate in Hz (default: 24000)
 * @param connectTimeoutSeconds WebSocket connect timeout
 */
public record MoshiRealtimeProperties(
        String apiKey,
        String url,
        String model,
        int sampleRate,
        int connectTimeoutSeconds
) {
    public MoshiRealtimeProperties {
        if (url == null || url.isBlank()) {
            url = "ws://localhost:8998/api/chat";
        }
        if (model == null || model.isBlank()) {
            model = "moshi";
        }
        if (sampleRate <= 0) {
            sampleRate = 24000;
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
    }
}
