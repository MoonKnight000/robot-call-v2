package uz.murodjon.robotcallv2.auth.infrastructure.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** A ticket the store cannot vouch for is refused — never accepted by default. */
class RedisStreamTicketStoreAdapterTest {

    private ValueOperations<String, String> values;
    private RedisStreamTicketStoreAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        adapter = new RedisStreamTicketStoreAdapter(redis);
    }

    @Test
    void consumeReadsAndDeletesInOneStep() {
        when(values.getAndDelete("auth:stream-ticket:h")).thenReturn("jwt");

        assertThat(adapter.consume("h")).isEqualTo("jwt");
        verify(values, never()).get(anyString());
    }

    @Test
    void consumeFailsClosedWhenRedisIsDown() {
        when(values.getAndDelete(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertThat(adapter.consume("h")).isNull();
    }

    @Test
    void saveReportsFailureWhenRedisIsDown() {
        doThrow(new RuntimeException("connection refused")).when(values).set(anyString(), anyString(), any(Duration.class));

        assertThat(adapter.save("h", "jwt", Duration.ofSeconds(60))).isFalse();
    }
}
