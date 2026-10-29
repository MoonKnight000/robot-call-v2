package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentWebhookEvent;

import java.util.Map;
import java.util.Set;

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
 * @param events           call-time events sent to the configured post-call webhook
 */
public record AgentWebhookConfig(
        String webhookUrl,
        String method,
        Map<String, String> headers,
        Map<String, String> dynamicVariables,
        Boolean transcript,
        Boolean audioUrl,
        Set<AgentWebhookEvent> events
) {
    public AgentWebhookConfig {
        if (headers == null) {
            headers = Map.of();
        }
        if (dynamicVariables == null) {
            dynamicVariables = Map.of();
        }
        if (events == null) {
            events = Set.of();
        } else {
            events = Set.copyOf(events);
        }
    }

    public AgentWebhookConfig(String webhookUrl, String method, Map<String, String> headers,
                              Map<String, String> dynamicVariables, Boolean transcript, Boolean audioUrl) {
        this(webhookUrl, method, headers, dynamicVariables, transcript, audioUrl, Set.of());
    }
}
