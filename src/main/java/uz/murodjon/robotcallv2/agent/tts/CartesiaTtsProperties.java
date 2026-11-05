package uz.murodjon.robotcallv2.agent.tts;

/**
 * Settings for Cartesia Sonic Ultra-Fast TTS (<90ms TTFB).
 *
 * @param apiKey               Cartesia API key
 * @param url                  REST endpoint (default: https://api.cartesia.ai/tts/bytes)
 * @param model                Model name (default sonic-3.6; sonic-multilingual was retired by Cartesia on 2026-06-01)
 * @param voice                Default voice ID
 * @param sampleRate           Sample rate in Hz (default: 8000 for telephony)
 * @param connectTimeoutSeconds Connect timeout
 */
public record CartesiaTtsProperties(
        String apiKey,
        String url,
        String model,
        String voice,
        int sampleRate,
        int connectTimeoutSeconds
) {
    public CartesiaTtsProperties {
        if (url == null || url.isBlank()) {
            url = "https://api.cartesia.ai/tts/bytes";
        }
        if (model == null || model.isBlank()) {
            model = "sonic-3.6";
        }
        if (sampleRate <= 0) {
            sampleRate = 8000;
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 5;
        }
    }
}
