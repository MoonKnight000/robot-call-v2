package uz.murodjon.robotcallv2.webhook.domain.entity;

import java.time.Instant;
import java.util.Map;

public record CallWebhookEvent(
        String eventType,
        long companyId,
        long callAttemptId,
        String channelId,
        String phone,
        String disposition,
        Integer durationSeconds,
        String recordingUrl,
        String summary,
        Map<String, Object> outcome,
        Instant timestamp
) {
}
