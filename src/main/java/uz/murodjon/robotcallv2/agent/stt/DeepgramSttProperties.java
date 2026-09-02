package uz.murodjon.robotcallv2.agent.stt;

/**
 * Settings for Deepgram Streaming STT (Nova-2 / Nova-3 models over WebSocket).
 *
 * @param apiKey               Deepgram API Key
 * @param url                  WebSocket listen endpoint (default: wss://api.deepgram.com/v1/listen)
 * @param model                Model name (e.g. nova-3, nova-2, general)
 * @param sampleRate           Sample rate in Hz (default: 8000)
 * @param interimResults       Whether to emit interim recognition hypotheses
 * @param connectTimeoutSeconds WebSocket connect timeout
 */
public record DeepgramSttProperties(
        String apiKey,
        String url,
        String model,
        int sampleRate,
        boolean interimResults,
        int connectTimeoutSeconds
) {
    public DeepgramSttProperties {
        if (url == null || url.isBlank()) {
            url = "wss://api.deepgram.com/v1/listen";
        }
        if (model == null || model.isBlank()) {
            model = "nova-3";
        }
        if (sampleRate <= 0) {
            sampleRate = 8000;
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 5;
        }
    }
}
