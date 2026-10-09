package uz.murodjon.robotcallv2.live.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.routing.CallRouteRegistry;
import uz.murodjon.robotcallv2.live.application.port.output.LiveEventRelayPort;
import uz.murodjon.robotcallv2.live.domain.entity.AudioLevelEvent;
import uz.murodjon.robotcallv2.live.domain.entity.LiveNotification;
import uz.murodjon.robotcallv2.live.domain.entity.LiveRelayEvent;
import uz.murodjon.robotcallv2.live.domain.entity.LiveTranscriptEvent;
import uz.murodjon.robotcallv2.live.domain.enums.LiveEventType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Live events over Redis pub/sub, one channel for the cluster. Each message names the
 * instance that sent it, and an instance ignores its own: it has already delivered them.
 *
 * <p>Publishing only queues the message; one virtual thread sends. A Redis round trip on
 * the RTP thread that publishes audio levels would delay the call's audio by exactly that
 * round trip. Past {@link #QUEUE_CAPACITY} unsent messages new ones are dropped — a live
 * panel missing an audio level is better than a call with choppy audio.
 *
 * <p>The payload travels as JSON and is read back into its own record by event type: the
 * HTTP layer writes the SSE stream with Jackson 3, and a Jackson 2 tree handed to it would
 * not come out as the JSON it holds.
 */
@Component
public class RedisLiveEventRelayAdapter implements LiveEventRelayPort, MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisLiveEventRelayAdapter.class);

    private static final String CHANNEL = "live:events";
    private static final int QUEUE_CAPACITY = 10_000;
    private static final Map<LiveEventType, Class<?>> PAYLOAD_TYPES = Map.of(
            LiveEventType.TRANSCRIPT, LiveTranscriptEvent.class,
            LiveEventType.AUDIO_LEVEL, AudioLevelEvent.class,
            LiveEventType.NOTIFICATION, LiveNotification.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String instanceId;
    private final BlockingQueue<String> outbox = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final List<Consumer<LiveRelayEvent>> consumers = new CopyOnWriteArrayList<>();

    public RedisLiveEventRelayAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                      CallRouteRegistry callRouteRegistry,
                                      RedisMessageListenerContainer redisMessageListenerContainer) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.instanceId = callRouteRegistry.instanceId();
        redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(CHANNEL));
        Thread.ofVirtual().name("live-relay").start(this::send);
    }

    @Override
    public void publish(LiveRelayEvent event) {
        if (!PAYLOAD_TYPES.containsKey(event.type())) {
            return;
        }
        try {
            ObjectNode message = objectMapper.createObjectNode();
            message.put("origin", instanceId);
            message.put("companyId", event.companyId());
            message.put("type", event.type().name());
            message.set("payload", objectMapper.valueToTree(event.payload()));
            if (!outbox.offer(objectMapper.writeValueAsString(message))) {
                log.debug("Live relay queue full — dropped a {} event", event.type());
            }
        } catch (Exception e) {
            log.warn("Live event not relayed: {}", e.getMessage());
        }
    }

    @Override
    public void subscribe(Consumer<LiveRelayEvent> consumer) {
        consumers.add(consumer);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode node = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
            if (instanceId.equals(node.path("origin").asText())) {
                return;
            }
            LiveEventType type = LiveEventType.valueOf(node.path("type").asText());
            Class<?> payloadType = PAYLOAD_TYPES.get(type);
            if (payloadType == null) {
                return;
            }
            LiveRelayEvent event = new LiveRelayEvent(node.path("companyId").asLong(), type,
                    objectMapper.treeToValue(node.get("payload"), payloadType));
            consumers.forEach(consumer -> consumer.accept(event));
        } catch (Exception e) {
            log.warn("Unreadable live event from another instance: {}", e.getMessage());
        }
    }

    private void send() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                redisTemplate.convertAndSend(CHANNEL, outbox.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.debug("Live event not sent to Redis: {}", e.getMessage());
            }
        }
    }
}
