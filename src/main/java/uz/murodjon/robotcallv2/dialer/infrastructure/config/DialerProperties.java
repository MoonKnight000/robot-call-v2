package uz.murodjon.robotcallv2.dialer.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dialer settings. Bound from {@code voice-agent.dialer.*} (PROJECT.md §5.2, §10).
 *
 * <p>The sweep scans ACTIVE campaigns every {@code tickSeconds} and dispatches due targets
 * over RabbitMQ, bounded by {@code dispatchBatch} (rate) and the concurrency limits below.
 *
 * @param maxConcurrentCalls           calls the whole platform may hold at once. The RTP
 *                                     port range caps it in turn
 *                                     ({@code RtpProperties#mediaCapacity()}): past that a
 *                                     call is answered and then dropped for want of a
 *                                     port, so raising this without widening
 *                                     {@code voice-agent.rtp.port-range-*} buys nothing
 * @param maxConcurrentCallsPerCompany one company's share of that. Without it the first
 *                                     campaign in the sweep takes every slot and the
 *                                     other tenants dial nothing; {@code 0} falls back to
 *                                     {@code maxConcurrentCalls}, i.e. no per-company
 *                                     limit at all
 */
@ConfigurationProperties(prefix = "voice-agent.dialer")
public record DialerProperties(
        boolean enabled,
        int tickSeconds,
        int dispatchBatch,
        int maxConcurrentCalls,
        int maxConcurrentCallsPerCompany,
        int reclaimAfterSec
) {

    /** The per-company limit, or the platform's when none is configured. */
    public int maxConcurrentCallsPerCompany() {
        return maxConcurrentCallsPerCompany > 0 ? maxConcurrentCallsPerCompany : maxConcurrentCalls;
    }
}
