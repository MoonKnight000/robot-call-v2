package uz.murodjon.robotcallv2.agent.routing;

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
 * instance_id|company_id} in Redis, so a request about a stateful call that reached the
 * wrong instance is told which instance owns it ({@code ActiveCallService#requireLiveCallOf}).
 * Best-effort — a Redis outage is logged and the call continues, it just isn't registered.
 *
 * <p>{@code voice-agent.routing.instance-id} names this instance; left blank it falls back
 * to the hostname, which is what a container gets for free.
 * {@code voice-agent.routing.ttl-minutes} is how long a mapping outlives its last write, so
 * a crashed instance's entries expire rather than routing traffic at nothing.
 */
@Component
public class CallRouteRegistry {

    private static final Logger log = LoggerFactory.getLogger(CallRouteRegistry.class);
    private static final String KEY_PREFIX = "call:route:";
    private static final char COMPANY_SEPARATOR = '|';

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;
    private final Duration ttl;

    public CallRouteRegistry(StringRedisTemplate redisTemplate,
                             @Value("${voice-agent.routing.instance-id:}") String configuredId,
                             @Value("${voice-agent.routing.ttl-minutes:30}") long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.instanceId = (configuredId != null && !configuredId.isBlank()) ? configuredId : resolveInstanceId();
        this.ttl = Duration.ofMinutes(ttlMinutes);
        log.info("Call route registry instance id: {}", instanceId);
    }

    public String instanceId() {
        return instanceId;
    }

    public void register(String channelId, long companyId) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + channelId, instanceId + COMPANY_SEPARATOR + companyId, ttl);
        } catch (Exception e) {
            log.warn("Route register failed for {}: {}", channelId, e.getMessage());
        }
    }

    public void unregister(String channelId) {
        try {
            redisTemplate.delete(KEY_PREFIX + channelId);
        } catch (Exception e) {
            log.debug("Route unregister failed for {}: {}", channelId, e.getMessage());
        }
    }

    /**
     * The instance holding {@code channelId}, when it is a call of {@code companyId}.
     * {@code null} for an unknown or expired call, a call of another company, or Redis being
     * unreachable. The company is stored with the instance so that the channel id of another
     * tenant tells the caller nothing.
     */
    public String findInstanceOf(long companyId, String channelId) {
        try {
            String route = redisTemplate.opsForValue().get(KEY_PREFIX + channelId);
            if (route == null) {
                return null;
            }
            int at = route.lastIndexOf(COMPANY_SEPARATOR);
            if (at <= 0 || !route.substring(at + 1).equals(Long.toString(companyId))) {
                return null;
            }
            return route.substring(0, at);
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
