package uz.murodjon.robotcallv2.agent.realtime;

/**
 * Settings for Alibaba Cloud Qwen-Omni Realtime Multimodal Speech-to-Speech Engine.
 *
 * @param apiKey               DashScope / Alibaba Cloud API Key
 * @param url                  WebSocket endpoint (default: wss://dashscope.aliyuncs.com/api-ws/v1/realtime)
 * @param model                Model name (e.g. qwen-omni-turbo, qwen2.5-omni)
 * @param voice                Voice name (e.g. cherry, ethan, serena)
 * @param connectTimeoutSeconds WebSocket connect timeout
 */
public record QwenOmniRealtimeProperties(
        String apiKey,
        String url,
        String model,
        String voice,
        int connectTimeoutSeconds
) {
    public QwenOmniRealtimeProperties {
        if (url == null || url.isBlank()) {
            url = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime";
        }
        if (model == null || model.isBlank()) {
            model = "qwen-omni-turbo";
        }
        if (voice == null || voice.isBlank()) {
            voice = "cherry";
        }
        if (connectTimeoutSeconds <= 0) {
            connectTimeoutSeconds = 10;
        }
    }
}
