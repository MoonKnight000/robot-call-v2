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
 */
@Component
public class DialerState {

    private static final Logger log = LoggerFactory.getLogger(DialerState.class);

    private static final String KEY = "dialer:active";

    private static final Duration TTL = Duration.ofHours(6);
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
                redis.opsForValue().set(KEY, "0");
            }
        } catch (Exception e) {
            log.warn("Concurrency release not recorded in Redis: {}", e.getMessage());
        }
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
