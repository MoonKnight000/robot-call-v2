package uz.murodjon.robotcallv2.crm.application.dto;

/**
 * Outcome of processing an inbound Uysot webhook event.
 */
public record UysotWebhookResult(
        String eventId,
        String eventType,
        boolean processed,
        Long targetId,
        String message
) {
    public static UysotWebhookResult success(String eventId, String eventType, Long targetId, String message) {
        return new UysotWebhookResult(eventId, eventType, true, targetId, message);
    }

    public static UysotWebhookResult skipped(String eventId, String eventType, String message) {
        return new UysotWebhookResult(eventId, eventType, false, null, message);
    }
}
