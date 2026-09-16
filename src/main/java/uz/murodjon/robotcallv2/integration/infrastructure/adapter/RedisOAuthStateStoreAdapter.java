package uz.murodjon.robotcallv2.integration.infrastructure.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.integration.application.port.output.OAuthStateStorePort;

import java.time.Duration;

/**
 * OAuth states in Redis, so the callback may land on any instance. GETDEL makes the
 * look-up and the removal one step: two callbacks racing with the same state cannot
 * both succeed.
 */
@Component
public class RedisOAuthStateStoreAdapter implements OAuthStateStorePort {

    private static final Logger log = LoggerFactory.getLogger(RedisOAuthStateStoreAdapter.class);
    private static final String KEY_PREFIX = "oauth:state:";

    private final StringRedisTemplate redisTemplate;

    public RedisOAuthStateStoreAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean save(String state, long companyId, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + state, String.valueOf(companyId), ttl);
            return true;
        } catch (RuntimeException e) {
            log.warn("Could not store an OAuth state: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Long consume(String state) {
        try {
            String companyId = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + state);
            return companyId != null ? Long.valueOf(companyId) : null;
        } catch (RuntimeException e) {
            log.warn("Could not read an OAuth state: {}", e.getMessage());
            return null;
        }
    }
}
