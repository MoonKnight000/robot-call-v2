package uz.murodjon.uysotvoice.dialer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dialer settings. Bound from {@code voice-agent.dialer.*} (PROJECT.md §5.2, §10).
 *
 * @param enabled          master switch for the scheduled dispatch loop
 * @param tickSeconds      how often the dispatcher scans for due targets
 * @param dispatchBatch    max calls dispatched per tick (rate limiting)
 * @param maxConcurrentCalls hard cap on simultaneously active outbound calls
 * @param reclaimAfterSec  a dispatched-but-unanswered call is reclaimed after this long
 * @param retry            per-disposition retry timing
 */
@ConfigurationProperties(prefix = "voice-agent.dialer")
public record
DialerProperties(
        boolean enabled,
        int tickSeconds,
        int dispatchBatch,
        int maxConcurrentCalls,
        int reclaimAfterSec,
        RetryProperties retry
) {
}
