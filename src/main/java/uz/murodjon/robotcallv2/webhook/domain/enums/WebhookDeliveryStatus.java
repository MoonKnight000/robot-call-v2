package uz.murodjon.robotcallv2.webhook.domain.enums;

/** Where a webhook that failed its first try stands. */
public enum WebhookDeliveryStatus {
    /** Waiting for its next try at {@code nextAttemptAt}. */
    PENDING,
    DELIVERED,
    /** Out of tries; the company was notified. */
    FAILED
}
