package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.shared.ratelimit.RateLimiter;

import java.time.Duration;

/**
 * Keeps each trunk under the calls per second its provider accepts (docs/VOICE-PIPELINE-PLAN.md
 * 4.4). Past that limit a provider answers 503 or silently drops the INVITE, and the number is
 * recorded as a failed attempt that was never really tried.
 *
 * <p>Counted per PJSIP endpoint in Redis, so every cell dialling over the same trunk shares one
 * budget. An originate over the limit waits for the next second rather than failing: the caller
 * is a dialer thread (or a person who clicked "call"), and a moment's wait costs neither.
 */
@Component
class TrunkCallPacer {

    private static final Logger log = LoggerFactory.getLogger(TrunkCallPacer.class);
    private static final Duration WINDOW = Duration.ofSeconds(1);
    private static final long RETRY_PAUSE_MS = 100;

    private final RateLimiter rateLimiter;
    private final DialerProperties dialerProperties;

    TrunkCallPacer(RateLimiter rateLimiter, DialerProperties dialerProperties) {
        this.rateLimiter = rateLimiter;
        this.dialerProperties = dialerProperties;
    }

    /** Returns once a call may go out over {@code endpoint}; {@code trunkMaxCps} null = the default. */
    void awaitSlot(String endpoint, Integer trunkMaxCps) {
        int limit = trunkMaxCps != null ? trunkMaxCps : dialerProperties.defaultTrunkCps();
        if (limit <= 0) {
            return;
        }
        String key = "dialer:cps:" + endpoint;
        long waitedMs = 0;
        while (!rateLimiter.tryAcquire(key, limit, WINDOW)) {
            try {
                Thread.sleep(RETRY_PAUSE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // shutting down — the originate that follows fails on its own
            }
            waitedMs += RETRY_PAUSE_MS;
        }
        if (waitedMs > 0) {
            log.debug("Originate over {} waited {} ms for its {} cps limit", endpoint, waitedMs, limit);
        }
    }
}
