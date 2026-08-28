package uz.murodjon.uysotvoice.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.webhook.dto.CallWebhookEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Dispatches real-time call lifecycle and disposition webhook events to external systems
 * (Bitrix24, AmoCRM, 1C, custom endpoints).
 */
@Service
public class WebhookEventService {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final boolean enabled;
    private final String defaultWebhookUrl;

    public WebhookEventService(
            @Value("${voice-agent.webhook.enabled:false}") boolean enabled,
            @Value("${voice-agent.webhook.url:}") String defaultWebhookUrl
    ) {
        this.enabled = enabled;
        this.defaultWebhookUrl = defaultWebhookUrl;
    }

    /**
     * Asynchronously delivers webhook event to external systems.
     */
    @Async("webhookExecutor")
    public void dispatchEvent(CallWebhookEvent event) {
        if (!enabled || defaultWebhookUrl == null || defaultWebhookUrl.isBlank()) {
            return;
        }

        try {
            String json = MAPPER.writeValueAsString(event);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(defaultWebhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-VoiceAgent-Event", event.eventType())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 == 2) {
                log.info("Webhook event {} dispatched successfully for callAttempt {}",
                        event.eventType(), event.callAttemptId());
            } else {
                log.warn("Webhook event {} failed with status {}", event.eventType(), resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch webhook event {}: {}", event.eventType(), e.getMessage());
        }
    }
}
