package uz.murodjon.robotcallv2.agent.ari;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.routing.CallRouteRegistry;
import uz.murodjon.robotcallv2.callrecord.application.dto.LiveCallRow;

import java.time.Duration;
import java.util.*;

/**
 * The live calls of a company across the cluster. Each instance holds only its own calls,
 * so a list read from one instance behind a load balancer showed a different half of the
 * company's calls on every refresh.
 *
 * <p>Every {@link #PUBLISH_MS} each instance writes its own calls, per company, into one
 * Redis hash per company (field = instance id); a reader adds up the fields written in the
 * last {@link #STALE_AFTER}, so a crashed instance's calls drop out within seconds. This
 * instance's own calls are always read from memory, never from its last write.
 */
@Component
class ClusterLiveCalls {

    private static final Logger log = LoggerFactory.getLogger(ClusterLiveCalls.class);

    private static final String KEY_PREFIX = "live:calls:";
    private static final long PUBLISH_MS = 2000;
    private static final Duration STALE_AFTER = Duration.ofSeconds(10);
    /** Outlives every fresh field, so a company whose last call ended does not keep a key. */
    private static final Duration KEY_TTL = Duration.ofSeconds(30);
    private static final TypeReference<List<LiveCallRow>> ROWS = new TypeReference<>() {
    };

    private final ActiveCallService activeCallService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String instanceId;
    /** Companies this instance wrote calls for last time — cleared once they have none left. */
    private Set<Long> published = Set.of();

    ClusterLiveCalls(ActiveCallService activeCallService, StringRedisTemplate redisTemplate,
                     ObjectMapper objectMapper, CallRouteRegistry callRouteRegistry) {
        this.activeCallService = activeCallService;
        this.redisTemplate = redisTemplate;
        // LiveCallRow carries an Instant; the shared mapper is plain defaults.
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
        this.instanceId = callRouteRegistry.instanceId();
    }

    @Scheduled(fixedDelay = PUBLISH_MS)
    void publish() {
        Map<Long, List<LiveCallRow>> byCompany = activeCallService.findLiveCallsByCompany();
        try {
            for (Map.Entry<Long, List<LiveCallRow>> entry : byCompany.entrySet()) {
                String key = KEY_PREFIX + entry.getKey();
                String value = objectMapper.writeValueAsString(Map.of(
                        "at", System.currentTimeMillis(), "calls", entry.getValue()));
                redisTemplate.opsForHash().put(key, instanceId, value);
                redisTemplate.expire(key, KEY_TTL);
            }
            for (Long companyId : published) {
                if (!byCompany.containsKey(companyId)) {
                    redisTemplate.opsForHash().delete(KEY_PREFIX + companyId, instanceId);
                }
            }
            published = new HashSet<>(byCompany.keySet());
        } catch (Exception e) {
            log.debug("Live calls not published to Redis: {}", e.getMessage());
        }
    }

    /** The calls of {@code companyId} on every instance — this one's straight from memory. */
    List<LiveCallRow> findLiveCalls(long companyId) {
        List<LiveCallRow> calls = new ArrayList<>(activeCallService.findLiveCalls(companyId));
        try {
            long cutoff = System.currentTimeMillis() - STALE_AFTER.toMillis();
            for (Map.Entry<Object, Object> entry : redisTemplate.opsForHash().entries(KEY_PREFIX + companyId).entrySet()) {
                if (instanceId.equals(entry.getKey())) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(String.valueOf(entry.getValue()));
                if (node.path("at").asLong() >= cutoff) {
                    calls.addAll(objectMapper.convertValue(node.get("calls"), ROWS));
                }
            }
        } catch (Exception e) {
            log.warn("Other instances' live calls unavailable, listing this instance's only: {}", e.getMessage());
        }
        // One order whatever order Redis hands the instances back in: LiveKpiPublisher
        // republishes the list whenever it differs from the last one.
        calls.sort(Comparator.comparing(LiveCallRow::startedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LiveCallRow::channelId));
        return calls;
    }
}
