package uz.murodjon.uysotvoice.dialer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks the number of active/reserved outbound calls to enforce
 * {@code max_concurrent_calls} (PROJECT.md §10). A slot is reserved at dispatch and
 * released exactly once when the call ends (or is reclaimed).
 *
 * <p>The counter lives in Redis because the limit describes the trunk, not one
 * process: with a per-JVM counter two instances each dial up to the limit and the
 * trunk sees twice the configured concurrency. Redis is already a dependency for
 * sticky routing (§5.1).
 *
 * <p>Redis being down must not stop calling, so the counter falls back to a local
 * one. The limit is then per-instance again — the old behaviour, and the log says so.
 */
@Component
public class DialerState {

    private static final Logger log = LoggerFactory.getLogger(DialerState.class);

    private static final String KEY = "dialer:active";

    /**
     * Safety net against a leaked count (an instance killed between reserve and
     * release): the key expires so a stuck value cannot block dialling forever.
     * Refreshed on every reserve, so it only elapses once dialling has really stopped.
     */
    private static final Duration TTL = Duration.ofHours(6);

    /** Daily per-campaign counters only need to outlive the day they describe. */
    private static final Duration DAILY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;
    private final AtomicInteger local = new AtomicInteger(0);

    public DialerState(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public int active() {
        try {
            String value = redis.opsForValue().get(KEY);
            return value == null ? 0 : Math.max(0, Integer.parseInt(value));
        } catch (Exception e) {
            log.warn("Redis unavailable for the concurrency counter, falling back to the local one: {}",
                    e.getMessage());
            return local.get();
        }
    }

    public void reserve() {
        local.incrementAndGet();
        try {
            redis.opsForValue().increment(KEY);
            redis.expire(KEY, TTL);
        } catch (Exception e) {
            log.warn("Concurrency reserve not recorded in Redis: {}", e.getMessage());
        }
    }

    public void release() {
        local.updateAndGet(n -> n > 0 ? n - 1 : 0);
        try {
            Long remaining = redis.opsForValue().decrement(KEY);
            if (remaining != null && remaining < 0) {
                // A release without a matching reserve (e.g. after a Redis restart)
                // would otherwise leave a negative count that masks real load.
                redis.opsForValue().set(KEY, "0");
            }
        } catch (Exception e) {
            log.warn("Concurrency release not recorded in Redis: {}", e.getMessage());
        }
    }

    /**
     * Count one dispatch against a campaign's daily cap (§C14).
     *
     * <p>Counts dispatches rather than {@code call_attempt} rows on purpose: an attempt row
     * is only written once a call is answered, so counting those would let a campaign dial
     * unlimited unanswered numbers — the ones that cost trunk minutes and reach nobody.
     */
    public void countDispatch(long campaignId, LocalDate day) {
        String key = dailyKey(campaignId, day);
        try {
            redis.opsForValue().increment(key);
            // Two days is enough to survive a clock/zone edge while never accumulating
            // keys: yesterday's counter is not read once the date rolls over.
            redis.expire(key, DAILY_TTL);
        } catch (Exception e) {
            log.warn("Daily dispatch count not recorded for campaign {}: {}", campaignId, e.getMessage());
        }
    }

    /**
     * Dispatches counted for this campaign today. Returns 0 when the counter is
     * unavailable — a Redis outage must not stop a campaign, and the concurrency limit
     * still bounds what can be in flight.
     */
    public int dispatchedToday(long campaignId, LocalDate day) {
        try {
            String value = redis.opsForValue().get(dailyKey(campaignId, day));
            return value == null ? 0 : Math.max(0, Integer.parseInt(value));
        } catch (Exception e) {
            log.warn("Daily dispatch count unavailable for campaign {}: {}", campaignId, e.getMessage());
            return 0;
        }
    }

    private static String dailyKey(long campaignId, LocalDate day) {
        return "dialer:campaign:" + campaignId + ":" + day;
    }
}
