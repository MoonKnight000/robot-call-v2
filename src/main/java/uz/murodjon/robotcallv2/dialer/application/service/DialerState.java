package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.dialer.application.port.input.DialerSlotUseCase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.OptionalInt;
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
public class DialerState implements DialerSlotUseCase {

    private static final Logger log = LoggerFactory.getLogger(DialerState.class);

    private static final String TOTAL_KEY = "dialer:active";
    private static final String COMPANY_KEY_PREFIX = "dialer:active:company:";
    private static final String CAMPAIGN_KEY_PREFIX = "dialer:active:campaign:";

    private static final Duration TTL = Duration.ofHours(6);
    private static final Duration DAILY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redisTemplate;
    private final AtomicInteger localTotal = new AtomicInteger(0);

    public DialerState(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Calls this company is holding right now. */
    public int countActive(long companyId) {
        return count(companyKey(companyId));
    }

    /** Calls the whole platform is holding right now — the figure the port range caps. */
    public int countActiveTotal() {
        return count(TOTAL_KEY);
    }

    /** Calls this campaign is holding right now — what its own maxConcurrentCalls is measured against. */
    public int countActiveForCampaign(long campaignId) {
        return count(campaignKey(campaignId));
    }

    public void reserve(long companyId, long campaignId) {
        localTotal.incrementAndGet();
        increment(TOTAL_KEY);
        increment(companyKey(companyId));
        increment(campaignKey(campaignId));
    }

    @Override
    public void release(long companyId, long campaignId) {
        localTotal.updateAndGet(n -> n > 0 ? n - 1 : 0);
        decrement(TOTAL_KEY);
        decrement(companyKey(companyId));
        decrement(campaignKey(campaignId));
    }

    private int count(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
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
            Long count = redisTemplate.opsForValue().increment(key);
            // The TTL is the safety net for a counter whose release never came (a crash
            // between dispatch and originate). Set when the key is born, not on every
            // reserve — refreshing it under steady traffic kept a leaked count alive for ever.
            if (count != null && count == 1) {
                redisTemplate.expire(key, TTL);
            }
        } catch (Exception e) {
            log.warn("Concurrency reserve not recorded in Redis for {}: {}", key, e.getMessage());
        }
    }

    private void decrement(String key) {
        try {
            Long remaining = redisTemplate.opsForValue().decrement(key);
            if (remaining != null && remaining < 0) {
                redisTemplate.opsForValue().set(key, "0");
            }
        } catch (Exception e) {
            log.warn("Concurrency release not recorded in Redis for {}: {}", key, e.getMessage());
        }
    }

    private static String companyKey(long companyId) {
        return COMPANY_KEY_PREFIX + companyId;
    }

    private static String campaignKey(long campaignId) {
        return CAMPAIGN_KEY_PREFIX + campaignId;
    }

    public void countDispatch(long campaignId, LocalDate day) {
        String key = dailyKey(campaignId, day);
        try {
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, DAILY_TTL);
        } catch (Exception e) {
            log.warn("Daily dispatch count not recorded for campaign {}: {}", campaignId, e.getMessage());
        }
    }

    /**
     * Calls this campaign dispatched on {@code day}; empty when the count cannot be read,
     * so the caller can refuse to dial rather than read an outage as "none yet".
     */
    public OptionalInt countDispatchedToday(long campaignId, LocalDate day) {
        try {
            String value = redisTemplate.opsForValue().get(dailyKey(campaignId, day));
            return OptionalInt.of(value == null ? 0 : Math.max(0, Integer.parseInt(value)));
        } catch (Exception e) {
            log.debug("Daily dispatch count unavailable for campaign {}: {}", campaignId, e.getMessage());
            return OptionalInt.empty();
        }
    }

    private static String dailyKey(long campaignId, LocalDate day) {
        return "dialer:campaign:" + campaignId + ":" + day;
    }
}
