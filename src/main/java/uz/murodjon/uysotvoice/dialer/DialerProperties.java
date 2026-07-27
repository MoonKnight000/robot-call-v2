package uz.murodjon.uysotvoice.dialer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dialer settings. Bound from {@code voice-agent.dialer.*} (PROJECT.md §5.2, §10).
 *
 * @param enabled          master switch for the scheduled dispatch loop
 * @param tickSeconds      how often the dispatcher scans for due targets
 * @param dispatchBatch    max calls dispatched per tick (rate limiting)
 * @param maxConcurrentCalls hard cap on simultaneously active outbound calls
 * @param reclaimAfterSec  a dispatched-but-unanswered call is reclaimed after this long
 * @param retry            per-disposition retry timing (see {@link RetrySchedule})
 */
@ConfigurationProperties(prefix = "voice-agent.dialer")
public record DialerProperties(
        boolean enabled,
        int tickSeconds,
        int dispatchBatch,
        int maxConcurrentCalls,
        int reclaimAfterSec,
        Retry retry
) {

    /**
     * How long to wait before retrying, by why the last attempt failed. The campaign's
     * own {@code retry_interval_hours} still covers everything not listed here.
     *
     * @param noAnswerMinutes   subscriber did not pick up — they may be free within hours
     * @param failedMinutes     technical/carrier failure — nothing about the subscriber
     *                          changed, so retry soon
     * @param voicemailMinutes  an answering machine answered. Calling back 24h later
     *                          reaches the same machine, so this should not be a multiple
     *                          of a day
     * @param respectDialWindow move a computed retry time into the campaign's dial window
     *                          and allowed weekdays (§11.2). Off means {@code
     *                          next_attempt_at} can read 03:00 Sunday, and the dialer
     *                          holds the target until the window reopens
     */
    public record Retry(
            int noAnswerMinutes,
            int failedMinutes,
            int voicemailMinutes,
            boolean respectDialWindow
    ) {
    }
}
