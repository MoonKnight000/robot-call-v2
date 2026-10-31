package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.shared.ratelimit.RateLimiter;

import java.time.Duration;

/**
 * Keeps a campaign under its own calls per second ({@code limits.maxCps}, Retell's campaign CPS cap) — on top of,
 * not instead of, each trunk's limit that {@code TrunkCallPacer} enforces at originate.
 *
 * <p>Counted per campaign in Redis, so every instance consuming the campaign's tasks shares one budget. A task over
 * the limit waits for the next second rather than failing: nothing was dialled yet, so a moment's wait costs no
 * attempt.
 */
@Component
public class CampaignCallPacer {

    private static final Logger log = LoggerFactory.getLogger(CampaignCallPacer.class);
    private static final Duration WINDOW = Duration.ofSeconds(1);
    private static final long RETRY_PAUSE_MS = 100;

    private final RateLimiter rateLimiter;

    public CampaignCallPacer(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /** Returns once {@code campaign} may start another call; at once when it sets no cap of its own. */
    public void awaitSlot(Campaign campaign) {
        Integer limit = campaign.limits().maxCps();
        if (limit == null || limit <= 0) {
            return;
        }
        String key = "dialer:campaign-cps:" + campaign.id();
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
            log.debug("Campaign {} waited {} ms for its {} cps limit", campaign.id(), waitedMs, limit);
        }
    }
}
