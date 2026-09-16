package uz.murodjon.robotcallv2.storage.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How long personal data is kept, enforced by {@code RetentionService}. Bound from
 * {@code voice-agent.retention.*}.
 *
 * <p>Every "days" value reads the same way: {@code 0} or less keeps the data forever. Only
 * {@code conversationDays} has a period by default — the other purges delete data that was
 * never deleted before, so each one waits for the owner to switch it on.
 *
 * @param cron             when the sweep runs (Spring cron, server time); read by the
 *                         {@code @Scheduled} placeholder, kept here so the group is in one place
 * @param conversationDays how long the transcript and recording of a call no agent ran are
 *                         kept, and anything audio no call owns (voicemail, recordings left on
 *                         local disk). A call an agent ran follows that agent's
 *                         {@code conversationRetentionDays} instead
 * @param zeroPiiGrace     how long a {@code zeroPiiRetention} agent's conversation may outlive
 *                         the call — room for the summary outbox to retry before the
 *                         transcript it summarizes is gone
 * @param summaryDays      {@code call_result} rows (summary, promised amount and date,
 *                         extracted outcome) older than this are deleted
 * @param clientMemoryDays {@code client_memory} rows not updated for this long are deleted
 * @param auditDays        {@code audit_log} rows older than this are deleted
 * @param outboxDays       SENT and DEAD {@code outbox_message} rows older than this are
 *                         deleted — delivery bookkeeping rather than personal data, so this
 *                         one is on by default (30 in {@code config/integrations.yml})
 * @param batchSize        rows one delete statement takes, so no purge holds a busy table
 *                         for long
 */
@ConfigurationProperties(prefix = "voice-agent.retention")
public record RetentionProperties(
        String cron,
        int conversationDays,
        Duration zeroPiiGrace,
        int summaryDays,
        int clientMemoryDays,
        int auditDays,
        int outboxDays,
        int batchSize
) {
    public RetentionProperties {
        if (zeroPiiGrace == null) {
            zeroPiiGrace = Duration.ofHours(1);
        }
        if (batchSize <= 0) {
            batchSize = 5000;
        }
    }
}
