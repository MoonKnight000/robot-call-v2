package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks the number of active/reserved outbound calls to enforce
 * max_concurrent_calls (PROJECT.md §10). A slot is reserved at dispatch and
 * released exactly once when the call ends (or is reclaimed).
 *
 * <p>Counted <b>per company as well as in total</b>. Data isolation (ROADMAP B) does not
 * help a tenant whose calls never get placed: a single counter meant one company's 50 000
 * target campaign held every slot on the platform, and every other company's campaign sat
 * ACTIVE and dialled nothing. The per-company count is what a company's own limit is
 * measured against; the total is what the machine's RTP port range is measured against
 * ({@code RtpProperties#mediaCapacity()}), so per-company limits added up can never
 * promise more media than exists.
 */
@Component
public class DialerState {

    private static final Logger log = LoggerFactory.getLogger(DialerState.class);

    private static final String TOTAL_KEY = "dialer:active";
    private static final String COMPANY_KEY_PREFIX = "dialer:active:company:";

    private static final Duration TTL = Duration.ofHours(6);
    private static final Duration DAILY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redis;
    private final AtomicInteger localTotal = new AtomicInteger(0);

    public DialerState(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Calls this company is holding right now. */
    public int active(long companyId) {
        return count(companyKey(companyId));
    }

    /** Calls the whole platform is holding right now — the figure the port range caps. */
    public int activeTotal() {
        return count(TOTAL_KEY);
    }

    public void reserve(long companyId) {
        localTotal.incrementAndGet();
        increment(TOTAL_KEY);
        increment(companyKey(companyId));
    }

    public void release(long companyId) {
        localTotal.updateAndGet(n -> n > 0 ? n - 1 : 0);
        decrement(TOTAL_KEY);
        decrement(companyKey(companyId));
    }

    private int count(String key) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? 0 : Math.max(0, Integer.parseInt(value));
        } catch (Exception e) {
            log.warn("Redis unavailable for the concurrency counter, falling back to the local one: {}",
                    e.getMessage());
            // Only the total has a local mirror. A per-company figure held in one JVM's
            // memory would be wrong the moment a second instance runs, and a limit that is
            // wrong per company is worse than one that falls back to the platform's.
            return localTotal.get();
        }
    }

    private void increment(String key) {
        try {
            redis.opsForValue().increment(key);
            redis.expire(key, TTL);
        } catch (Exception e) {
            log.warn("Concurrency reserve not recorded in Redis for {}: {}", key, e.getMessage());
        }
    }

    private void decrement(String key) {
        try {
            Long remaining = redis.opsForValue().decrement(key);
            if (remaining != null && remaining < 0) {
                redis.opsForValue().set(key, "0");
            }
        } catch (Exception e) {
            log.warn("Concurrency release not recorded in Redis for {}: {}", key, e.getMessage());
        }
    }

    private static String companyKey(long companyId) {
        return COMPANY_KEY_PREFIX + companyId;
    }

    public void countDispatch(long campaignId, LocalDate day) {
        String key = dailyKey(campaignId, day);
        try {
            redis.opsForValue().increment(key);
            redis.expire(key, DAILY_TTL);
        } catch (Exception e) {
            log.warn("Daily dispatch count not recorded for campaign {}: {}", campaignId, e.getMessage());
        }
    }

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
