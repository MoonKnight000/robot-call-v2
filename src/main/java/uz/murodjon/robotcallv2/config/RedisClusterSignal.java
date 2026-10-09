package uz.murodjon.robotcallv2.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.cluster.ClusterSignal;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link ClusterSignal} over Redis pub/sub: one channel per topic, the message is the
 * sending JVM's id, so an instance can recognise and skip its own signal.
 *
 * <p>Handlers run on a virtual thread each, not on the subscription's own thread: a handler
 * that writes files and reloads Asterisk must not hold up every other message behind it.
 */
@Component
public class RedisClusterSignal implements ClusterSignal {

    private static final Logger log = LoggerFactory.getLogger(RedisClusterSignal.class);
    private static final String CHANNEL_PREFIX = "cluster:signal:";

    /** Per JVM, not per configured instance id: only "was it me" is ever asked of it. */
    private final String origin = UUID.randomUUID().toString();
    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final ExecutorService handlers = Executors.newVirtualThreadPerTaskExecutor();

    public RedisClusterSignal(StringRedisTemplate redisTemplate,
                              RedisMessageListenerContainer redisMessageListenerContainer) {
        this.redisTemplate = redisTemplate;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
    }

    @Override
    public void send(String topic) {
        try {
            redisTemplate.convertAndSend(CHANNEL_PREFIX + topic, origin);
        } catch (Exception e) {
            log.warn("Cluster signal {} not sent — the other instances catch up at their next start: {}",
                    topic, e.getMessage());
        }
    }

    @Override
    public void subscribe(String topic, Runnable handler) {
        redisMessageListenerContainer.addMessageListener((message, pattern) -> {
            if (origin.equals(new String(message.getBody(), StandardCharsets.UTF_8))) {
                return;
            }
            handlers.execute(() -> {
                try {
                    handler.run();
                } catch (Exception e) {
                    log.warn("Cluster signal {} handler failed: {}", topic, e.getMessage());
                }
            });
        }, new ChannelTopic(CHANNEL_PREFIX + topic));
    }
}
