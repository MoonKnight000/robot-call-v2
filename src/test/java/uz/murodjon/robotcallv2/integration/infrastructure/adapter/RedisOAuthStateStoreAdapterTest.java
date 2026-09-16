package uz.murodjon.robotcallv2.integration.infrastructure.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisOAuthStateStoreAdapterTest {

    private ValueOperations<String, String> values;
    private RedisOAuthStateStoreAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        adapter = new RedisOAuthStateStoreAdapter(redisTemplate);
    }

    @Test
    void stateIsKeptForItsCompanyUntilItExpires() {
        assertTrue(adapter.save("abc", 7L, Duration.ofHours(1)));

        verify(values).set("oauth:state:abc", "7", Duration.ofHours(1));
    }

    /** GETDEL: reading a state removes it in the same step, so a replayed callback finds nothing. */
    @Test
    void stateIsReadAndRemovedInOneStep() {
        when(values.getAndDelete("oauth:state:abc")).thenReturn("7", (String) null);

        assertEquals(7L, adapter.consume("abc"));
        assertNull(adapter.consume("abc"));
        verify(values, never()).get(any());
    }

    @Test
    void unreachableRedisNeitherStartsNorFinishesAConnection() {
        doThrow(new IllegalStateException("redis down")).when(values)
                .set(anyString(), anyString(), any(Duration.class));
        when(values.getAndDelete(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertFalse(adapter.save("abc", 7L, Duration.ofHours(1)));
        assertNull(adapter.consume("abc"));
    }
}
