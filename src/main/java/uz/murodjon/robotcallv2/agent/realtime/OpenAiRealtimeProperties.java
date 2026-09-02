package uz.murodjon.robotcallv2.agent.realtime;

/**
 * Settings for OpenAI Realtime API (WebSocket Bidi audio streaming).
 *
 * @param apiKey               OpenAI API Key
 * @param url                  WebSocket endpoint (default: wss://api.openai.com/v1/realtime)
 * @param model                Model name (e.g. gpt-4o-realtime-preview, gpt-4o-mini-realtime-preview)
 * @param voice                Voice name (e.g. alloy, echo, shimmer, ash, ballad, coral, sage, verse)
 * @param connectTimeoutSeconds WebSocket connect timeout
 * @param temperature          Sampling temperature (0.6 - 0.8)
 */
public record OpenAiRealtimeProperties(
        String apiKey,
        String url,
        String model,
        String voice,
        int connectTimeoutSeconds,
        double temperature
) {
    public OpenAiRealtimeProperties {
        if (url == null || url.isBlank()) {
            url = "wss://api.openai.com/v1/realtime";
        }
        if (model == null || model.isBlank()) {
            model = "gpt-4o-realtime-preview";
        }
        if (voice == null || voice.isBlank()) {
            voice = "alloy";
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
        if (temperature <= 0) {
            temperature = 0.7;
        }
    }
}
