package uz.murodjon.robotcallv2.agent.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 * {@code voice-agent.routing.internal-url} is where the other instances reach this one
 * ({@code http://app-1:8080}) to hand it a request about one of its calls
 * ({@code security/CallOwnerProxyFilter}); blank, nobody can, and such a request is
 * answered {@code 409 CALL_ON_OTHER_INSTANCE} as before.
 */
@Component
public class CallRouteRegistry {

    private static final Logger log = LoggerFactory.getLogger(CallRouteRegistry.class);
    private static final String KEY_PREFIX = "call:route:";
    private static final String INSTANCE_URLS_KEY = "call:instance-url";
    private static final char COMPANY_SEPARATOR = '|';

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;
    private final Duration ttl;
    private final String internalUrl;
    /** The calls this instance registered and has not torn down yet: channel id → company id. */
    private final Map<String, Long> owned = new ConcurrentHashMap<>();

    public CallRouteRegistry(StringRedisTemplate redisTemplate,
                             @Value("${voice-agent.routing.instance-id:}") String configuredId,
                             @Value("${voice-agent.routing.ttl-minutes:30}") long ttlMinutes,
                             @Value("${voice-agent.routing.internal-url:}") String internalUrl) {
        this.redisTemplate = redisTemplate;
        this.instanceId = (configuredId != null && !configuredId.isBlank()) ? configuredId : resolveInstanceId();
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.internalUrl = internalUrl == null ? "" : internalUrl.strip();
        log.info("Call route registry instance id: {}", instanceId);
    }

    public String instanceId() {
        return instanceId;
    }

    /** Where {@code instanceId} can be reached by another instance; null when it never said. */
    public String findInternalUrl(String instanceId) {
        try {
            Object url = redisTemplate.opsForHash().get(INSTANCE_URLS_KEY, instanceId);
            return url != null ? url.toString() : null;
        } catch (Exception e) {
            log.warn("Instance URL lookup failed for {}: {}", instanceId, e.getMessage());
            return null;
        }
    }

    public void register(String channelId, long companyId) {
        owned.put(channelId, companyId);
        write(channelId, companyId);
    }

    /**
     * Re-writes the routes of the calls this instance still holds, every third of the TTL.
     * Written once at answer, a route expired under any call longer than the TTL — a long
     * talk with an operator — and requests about that call stopped finding it.
     */
    @Scheduled(fixedDelayString = "#{${voice-agent.routing.ttl-minutes:30} * 20000}")
    public void refreshOwnedRoutes() {
        owned.forEach(this::write);
        if (!internalUrl.isEmpty()) {
            // Here too, so a Redis that restarted empty learns it again.
            try {
                redisTemplate.opsForHash().put(INSTANCE_URLS_KEY, instanceId, internalUrl);
            } catch (Exception e) {
                log.warn("Instance URL not announced: {}", e.getMessage());
            }
        }
    }

    private void write(String channelId, long companyId) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + channelId, instanceId + COMPANY_SEPARATOR + companyId, ttl);
        } catch (Exception e) {
            log.warn("Route register failed for {}: {}", channelId, e.getMessage());
        }
    }

    public void unregister(String channelId) {
        owned.remove(channelId);
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
