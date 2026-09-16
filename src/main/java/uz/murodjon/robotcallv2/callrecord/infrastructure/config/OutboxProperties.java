package uz.murodjon.robotcallv2.callrecord.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The post-call outbox, bound from {@code voice-agent.outbox.*}.
 *
 * @param enabled                 when false, nothing is retried: neither summaries nor CRM
 *                                writes. The first CRM delivery, right after the call, still
 *                                happens
 * @param intervalMinutes         how often the summary retry runs; a call it claimed is not
 *                                retried again for this long
 * @param maxAttempts             attempts per summary, and per CRM write, before giving up
 * @param batch                   summaries, and CRM writes, one run takes on
 * @param deliveryIntervalSeconds how often due CRM writes are retried
 * @param backoffSeconds          wait after a CRM write's first failed attempt; doubled after
 *                                each further one, capped at an hour
 */
@ConfigurationProperties(prefix = "voice-agent.outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean enabled,
        int intervalMinutes,
        int maxAttempts,
        int batch,
        int deliveryIntervalSeconds,
        int backoffSeconds
) {

    public OutboxProperties {
        if (intervalMinutes <= 0) {
            intervalMinutes = 5;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
        if (batch <= 0) {
            batch = 20;
        }
        if (deliveryIntervalSeconds <= 0) {
            deliveryIntervalSeconds = 30;
        }
        if (backoffSeconds <= 0) {
            backoffSeconds = 120;
        }
    }
}
