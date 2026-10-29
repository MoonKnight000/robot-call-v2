package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Publishes event-based webhook triggers for call events
 * (RETELL_AI_GAP_ANALYSIS.md #27).
 *
 * <p>Supports events: SENTIMENT_CHANGE, FACT_EXTRACTED, TOOL_EXECUTED,
 * STAGE_TRANSITION, TRANSFER_REQUESTED, VOICEMAIL_DETECTED, INTENT_DETECTED.
 */
@Component
public class EventWebhookPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventWebhookPublisher.class);

    /** Supported event types. */
    public enum EventType {
        SENTIMENT_CHANGE,
        FACT_EXTRACTED,
        TOOL_EXECUTED,
        STAGE_TRANSITION,
        TRANSFER_REQUESTED,
        VOICEMAIL_DETECTED,
        INTENT_DETECTED
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService webhookExecutor;

    public EventWebhookPublisher() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.webhookExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Publish an event to the configured webhook URL.
     *
     * @param companyId   the company ID
     * @param channelId   the call channel ID
     * @param eventType   the type of event
     * @param webhookUrl  the webhook URL to call
     * @param data        additional event data
     * @param secret      optional HMAC secret for signature
     */
    public void publish(long companyId, String channelId, EventType eventType,
                        String webhookUrl, Map<String, Object> data, String secret) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("companyId", companyId);
        payload.put("channelId", channelId);
        payload.put("eventType", eventType.name());
        payload.put("timestamp", Instant.now().toString());
        if (data != null && !data.isEmpty()) {
            payload.put("data", data);
        }

        // Fire and forget — don't block the call
        CompletableFuture.runAsync(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Event-Type", eventType.name());
                headers.set("X-Event-Timestamp", Instant.now().toString());

                String body = objectMapper.writeValueAsString(payload);
                if (secret != null && !secret.isBlank()) {
                    try {
                        // Simple HMAC signature
                        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
                        byte[] signBytes = mac.doFinal(body.getBytes());
                        String signature = java.util.Base64.getEncoder().encodeToString(signBytes);
                        headers.set("X-Webhook-Signature", signature);
                    } catch (Exception e) {
                        log.warn("Failed to compute webhook signature: {}", e.getMessage());
                    }
                }

                HttpEntity<String> entity = new HttpEntity<>(body, headers);
                restTemplate.postForEntity(webhookUrl, entity, String.class);
                log.info("Webhook delivered: {} -> {}", eventType, webhookUrl);
            } catch (Exception e) {
                log.warn("Webhook delivery failed: {} -> {}: {}", eventType, webhookUrl, e.getMessage());
            }
        }, webhookExecutor);
    }

    /** Convenience: publish sentiment change event. */
    public void publishSentimentChange(long companyId, String channelId, String webhookUrl,
                                       CustomerSentiment sentiment, String secret) {
        Map<String, Object> data = Map.of("sentiment", sentiment.name());
        publish(companyId, channelId, EventType.SENTIMENT_CHANGE, webhookUrl, data, secret);
    }

    /** Convenience: publish fact extracted event. */
    public void publishFactExtracted(long companyId, String channelId, String webhookUrl,
                                     String factKey, Object factValue, String secret) {
        Map<String, Object> data = Map.of("factKey", factKey, "factValue", factValue);
        publish(companyId, channelId, EventType.FACT_EXTRACTED, webhookUrl, data, secret);
    }

    /** Convenience: publish stage transition event. */
    public void publishStageTransition(long companyId, String channelId, String webhookUrl,
                                       String fromStage, String toStage, String secret) {
        Map<String, Object> data = Map.of("fromStage", fromStage, "toStage", toStage);
        publish(companyId, channelId, EventType.STAGE_TRANSITION, webhookUrl, data, secret);
    }

    /** Convenience: publish intent detected event. */
    public void publishIntentDetected(long companyId, String channelId, String webhookUrl,
                                      DetectedIntent intent, String secret) {
        Map<String, Object> data = Map.of(
                "intent", intent.intent().name(),
                "confidence", intent.confidence()
        );
        publish(companyId, channelId, EventType.INTENT_DETECTED, webhookUrl, data, secret);
    }
}
