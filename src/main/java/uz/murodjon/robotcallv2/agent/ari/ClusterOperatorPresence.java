package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Whether an operator's phone is signed in, asked from any cell of the cluster. The phones
 * sign in to the operator cell's Asterisk only ({@link AsteriskProperties#operatorTrunk}),
 * so another cell's Asterisk does not know them at all and would call every operator offline.
 *
 * <p>The operator cell lists its online PJSIP endpoints every {@link #PUBLISH_MS} into one
 * Redis set; the other cells read it. The set outlives a few missed rounds and no more, so
 * when the operator cell is down its operators read as offline — which is what they are,
 * to every other cell.
 */
@Component
class ClusterOperatorPresence {

    private static final Logger log = LoggerFactory.getLogger(ClusterOperatorPresence.class);

    private static final String KEY = "operator:online";
    private static final long PUBLISH_MS = 5000;
    private static final Duration KEY_TTL = Duration.ofSeconds(15);

    private final AsteriskProperties asteriskProperties;
    private final AriClient ariClient;
    private final StringRedisTemplate redisTemplate;

    ClusterOperatorPresence(AsteriskProperties asteriskProperties, AriClient ariClient,
                            StringRedisTemplate redisTemplate) {
        this.asteriskProperties = asteriskProperties;
        this.ariClient = ariClient;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelay = PUBLISH_MS)
    void publish() {
        if (!asteriskProperties.isOperatorCell()) {
            return;
        }
        try {
            List<String> online = ariClient.findOnlineEndpointNames();
            if (online.isEmpty()) {
                redisTemplate.delete(KEY);
                return;
            }
            // Built aside and renamed into place, so a reader never finds the set half written.
            String next = KEY + ":next";
            redisTemplate.delete(next);
            redisTemplate.opsForSet().add(next, online.toArray(String[]::new));
            redisTemplate.expire(next, KEY_TTL);
            redisTemplate.rename(next, KEY);
        } catch (Exception e) {
            log.debug("Online operators not published: {}", e.getMessage());
        }
    }

    /** Asked of this cell's own Asterisk on the operator cell, of the published set anywhere else. */
    boolean isOnline(String endpointName) {
        if (asteriskProperties.isOperatorCell()) {
            return ariClient.isEndpointOnline(endpointName);
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(KEY, endpointName));
        } catch (Exception e) {
            log.warn("Operator presence unavailable, treating {} as offline: {}", endpointName, e.getMessage());
            return false;
        }
    }
}
