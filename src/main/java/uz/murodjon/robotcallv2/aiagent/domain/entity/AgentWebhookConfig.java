package uz.murodjon.robotcallv2.aiagent.domain.entity;

import java.util.Map;

/**
 * Webhook configuration for agent initiation or post-call callback.
 * The URL, headers and timeout one of an agent's webhooks is called with.
 *
 * @param webhookUrl       destination URL
 * @param method           HTTP method (POST, GET)
 * @param headers          HTTP headers
 * @param dynamicVariables variables to pass (initiation)
 * @param transcript       whether to send call transcript (post-call)
 * @param audioUrl         whether to send recorded audio URL (post-call)
 */
public record AgentWebhookConfig(
        String webhookUrl,
        String method,
        Map<String, String> headers,
        Map<String, String> dynamicVariables,
        Boolean transcript,
        Boolean audioUrl
) {
    public AgentWebhookConfig {
        if (headers == null) {
            headers = Map.of();
        }
        if (dynamicVariables == null) {
            dynamicVariables = Map.of();
        }
    }
}
