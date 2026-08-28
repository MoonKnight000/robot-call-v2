package uz.murodjon.uysotvoice.webhook.dto;

import java.time.Instant;
import java.util.Map;

public record CallWebhookEvent(
        String eventType, // CALL_STARTED, CALL_ANSWERED, CALL_COMPLETED, DISPOSITION_RECORDED
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
