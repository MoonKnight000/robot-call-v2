package uz.murodjon.robotcallv2.shared.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-window event counter shared by every instance through Redis: "how many times has
 * {@code key} happened in the current window". Login attempts per address, failed logins
 * per account, calls to a public endpoint — anything where the answer has to be the same
 * whichever instance the request landed on.
 *
 * <p>Falls back to an in-process counter while Redis is unreachable rather than letting
 * everything through: a limit that switches itself off during an outage is no limit.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private record Window(long resetAtMillis, AtomicLong count) {
    }

    private final StringRedisTemplate redisTemplate;
    private final Map<String, Window> local = new ConcurrentHashMap<>();

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Counts one more event under {@code key} and returns the total in the current window. */
    public long hit(String key, Duration window) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null) {
                // The window starts with the first event. A key left without a TTL (an
                // expire that failed) is repaired on the next hit rather than living for ever.
                Long ttl = redisTemplate.getExpire(key);
                if (count == 1 || ttl == null || ttl < 0) {
                    redisTemplate.expire(key, window);
                }
                return count;
            }
        } catch (RuntimeException e) {
            log.warn("Redis unavailable for rate limit {} — counting in-process: {}", key, e.getMessage());
        }
        return localHit(key, window);
    }

    /** Events under {@code key} in the current window, without counting one. */
    public long count(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException e) {
            Window w = local.get(key);
            return w == null || w.resetAtMillis() <= System.currentTimeMillis() ? 0 : w.count().get();
        }
    }

    /** Forgets {@code key} — a successful login wipes the failures that preceded it. */
    public void clear(String key) {
        local.remove(key);
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("Redis unavailable for rate limit {} — cleared in-process only: {}", key, e.getMessage());
        }
    }

    /** Counts the event and says whether it still fits under {@code limit} per {@code window}. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        return hit(key, window) <= limit;
    }

    private long localHit(String key, Duration window) {
        long now = System.currentTimeMillis();
        Window w = local.compute(key, (k, existing) ->
                existing == null || existing.resetAtMillis() <= now
                        ? new Window(now + window.toMillis(), new AtomicLong())
                        : existing);
        return w.count().incrementAndGet();
    }
}
