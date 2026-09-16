package uz.murodjon.robotcallv2.widget.infrastructure.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.widget.application.port.output.WidgetCallCounterPort;
import uz.murodjon.robotcallv2.widget.domain.entity.WidgetCallBooking;
import uz.murodjon.robotcallv2.widget.domain.enums.WidgetBookingResult;

import java.time.Duration;
import java.util.List;

/**
 * Widget call counts in Redis, shared by every instance: an agent's booked sessions are a
 * sorted set scored by booking time ({@code widget:inflight:<agentId>}), its calls of the
 * day a counter ({@code widget:daily:<agentId>:<date>}).
 *
 * <p>Check and book run as one Lua script, so Redis executes them without another client's
 * command in between: a burst of requests for the last line gets exactly one booking,
 * where two separate round trips let every one of them see the line free.
 */
@Component
public class RedisWidgetCallCounterAdapter implements WidgetCallCounterPort {

    private static final Logger log = LoggerFactory.getLogger(RedisWidgetCallCounterAdapter.class);

    private static final String IN_FLIGHT_KEY = "widget:inflight:";
    private static final String DAILY_KEY = "widget:daily:";

    /** The set of live sessions outlives its newest member, then goes. */
    private static final Duration IN_FLIGHT_KEY_TTL = WidgetCallBooking.SESSION_TTL.multipliedBy(2);
    /** A day's counter outlives its day, so a check just after midnight still reads it cleanly. */
    private static final Duration DAILY_KEY_TTL = Duration.ofDays(2);

    private static final long BOOKED = 0;
    private static final long CONCURRENT_LIMIT_REACHED = 1;
    private static final long DAILY_LIMIT_REACHED = 2;

    /**
     * KEYS: 1 — the agent's session set, 2 — its day counter. ARGV: 1 — booked-at (ms),
     * 2 — abandoned-before (ms), 3 — concurrent limit, 4 — daily limit, 5 — reservation id,
     * 6 — set TTL (ms), 7 — counter TTL (s). Answers 0 booked, 1 lines full, 2 day spent.
     */
    private static final RedisScript<Long> BOOK_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3]) then
                return 1
            end
            if tonumber(redis.call('GET', KEYS[2]) or '0') >= tonumber(ARGV[4]) then
                return 2
            end
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[5])
            redis.call('PEXPIRE', KEYS[1], ARGV[6])
            if redis.call('INCR', KEYS[2]) == 1 then
                redis.call('EXPIRE', KEYS[2], ARGV[7])
            end
            return 0
            """, Long.class);

    /** KEYS as above; ARGV: 1 — reservation id. Only a booking still in the set gives its call back. */
    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('ZREM', KEYS[1], ARGV[1]) == 1
                    and tonumber(redis.call('GET', KEYS[2]) or '0') > 0 then
                redis.call('DECR', KEYS[2])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisWidgetCallCounterAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public WidgetBookingResult book(WidgetCallBooking booking) {
        try {
            Long answer = redisTemplate.execute(BOOK_SCRIPT, keysOf(booking),
                    String.valueOf(booking.bookedAt().toEpochMilli()),
                    String.valueOf(booking.computeAbandonedBefore().toEpochMilli()),
                    String.valueOf(booking.concurrentLimit()),
                    String.valueOf(booking.dailyLimit()),
                    booking.reservationId(),
                    String.valueOf(IN_FLIGHT_KEY_TTL.toMillis()),
                    String.valueOf(DAILY_KEY_TTL.toSeconds()));
            if (answer == null) {
                return WidgetBookingResult.UNAVAILABLE;
            }
            if (answer == BOOKED) {
                return WidgetBookingResult.BOOKED;
            }
            if (answer == CONCURRENT_LIMIT_REACHED) {
                return WidgetBookingResult.CONCURRENT_LIMIT_REACHED;
            }
            return answer == DAILY_LIMIT_REACHED ? WidgetBookingResult.DAILY_LIMIT_REACHED : WidgetBookingResult.UNAVAILABLE;
        } catch (RuntimeException e) {
            log.warn("Redis unavailable for widget limits of agent {}: {}", booking.agentId(), e.getMessage());
            return WidgetBookingResult.UNAVAILABLE;
        }
    }

    @Override
    public void release(WidgetCallBooking booking) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, keysOf(booking), booking.reservationId());
        } catch (RuntimeException e) {
            log.warn("Could not release widget booking of agent {} — it expires on its own: {}",
                    booking.agentId(), e.getMessage());
        }
    }

    private static List<String> keysOf(WidgetCallBooking booking) {
        return List.of(IN_FLIGHT_KEY + booking.agentId(), DAILY_KEY + booking.agentId() + ":" + booking.day());
    }
}
