package uz.murodjon.robotcallv2.dialer.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dialer settings. Bound from {@code voice-agent.dialer.*} (PROJECT.md §5.2, §10).
 */
@ConfigurationProperties(prefix = "voice-agent.dialer")
public record DialerProperties(
        boolean enabled,
        int tickSeconds,
        int dispatchBatch,
        int maxConcurrentCalls,
        int reclaimAfterSec,
        RetryProperties retry
) {
}
