package uz.murodjon.uysotvoice.agent.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;

/**
 * Sticky-routing registry (PROJECT.md §5.1, Stage 12): maps {@code call_id →
 * instance_id} in Redis so a load balancer can route follow-up traffic back to the
 * instance that owns the (stateful) call. Best-effort — a Redis outage is logged and
 * the call continues, it just isn't registered.
 */
@Component
public class CallRouteRegistry {

    private static final Logger log = LoggerFactory.getLogger(CallRouteRegistry.class);
    private static final String KEY_PREFIX = "call:route:";

    private final StringRedisTemplate redis;
    private final String instanceId;
    private final Duration ttl;

    public CallRouteRegistry(StringRedisTemplate redis,
                             @Value("${voice-agent.routing.instance-id:}") String configuredId,
                             @Value("${voice-agent.routing.ttl-minutes:30}") long ttlMinutes) {
        this.redis = redis;
        this.instanceId = (configuredId != null && !configuredId.isBlank()) ? configuredId : resolveInstanceId();
        this.ttl = Duration.ofMinutes(ttlMinutes);
        log.info("Call route registry instance id: {}", instanceId);
    }

    public String instanceId() {
        return instanceId;
    }

    public void register(String channelId) {
        try {
            redis.opsForValue().set(KEY_PREFIX + channelId, instanceId, ttl);
        } catch (Exception e) {
            log.warn("Route register failed for {}: {}", channelId, e.getMessage());
        }
    }

    public void unregister(String channelId) {
        try {
            redis.delete(KEY_PREFIX + channelId);
        } catch (Exception e) {
            log.debug("Route unregister failed for {}: {}", channelId, e.getMessage());
        }
    }

    public String lookup(String channelId) {
        try {
            return redis.opsForValue().get(KEY_PREFIX + channelId);
        } catch (Exception e) {
            log.warn("Route lookup failed for {}: {}", channelId, e.getMessage());
            return null;
        }
    }

    private static String resolveInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "instance-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
