package uz.murodjon.robotcallv2.auth.infrastructure.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.auth.application.port.output.StreamTicketStorePort;

import java.time.Duration;

/**
 * Stream tickets in Redis, so the stream may open on any instance. GETDEL makes reading
 * and spending a ticket one step. There is deliberately no in-process fallback: a ticket
 * this store cannot vouch for is refused (fail closed).
 */
@Component
public class RedisStreamTicketStoreAdapter implements StreamTicketStorePort {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamTicketStoreAdapter.class);
    private static final String KEY_PREFIX = "auth:stream-ticket:";

    private final StringRedisTemplate redisTemplate;

    public RedisStreamTicketStoreAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean save(String ticketHash, String accessToken, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + ticketHash, accessToken, ttl);
            return true;
        } catch (RuntimeException e) {
            log.warn("Could not store a stream ticket: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String consume(String ticketHash) {
        try {
            return redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + ticketHash);
        } catch (RuntimeException e) {
            log.warn("Could not read a stream ticket — refusing it: {}", e.getMessage());
            return null;
        }
    }
}
