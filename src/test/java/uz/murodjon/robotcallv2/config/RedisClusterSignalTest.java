package uz.murodjon.robotcallv2.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** A signal reaches the other instances and not the one that sent it. */
class RedisClusterSignalTest {

    private StringRedisTemplate redisTemplate;
    private RedisMessageListenerContainer container;
    private RedisClusterSignal signal;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        container = mock(RedisMessageListenerContainer.class);
        signal = new RedisClusterSignal(redisTemplate, container);
    }

    @Test
    void anotherInstancesSignalRunsTheHandler() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        MessageListener listener = subscribe(ran::countDown);

        listener.onMessage(message("some-other-jvm"), null);

        assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void itsOwnSignalIsSkipped() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        MessageListener listener = subscribe(ran::countDown);
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);

        signal.send("pjsip.trunks");
        verify(redisTemplate).convertAndSend(eq("cluster:signal:pjsip.trunks"), sent.capture());
        listener.onMessage(message(sent.getValue()), null);

        assertThat(ran.await(300, TimeUnit.MILLISECONDS)).isFalse();
    }

    private MessageListener subscribe(Runnable handler) {
        signal.subscribe("pjsip.trunks", handler);
        ArgumentCaptor<MessageListener> listener = ArgumentCaptor.forClass(MessageListener.class);
        verify(container).addMessageListener(listener.capture(), any(ChannelTopic.class));
        return listener.getValue();
    }

    private static DefaultMessage message(String body) {
        return new DefaultMessage("cluster:signal:pjsip.trunks".getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
    }
}
