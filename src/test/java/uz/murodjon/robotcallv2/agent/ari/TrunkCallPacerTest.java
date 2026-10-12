package uz.murodjon.robotcallv2.agent.ari;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.shared.ratelimit.RateLimiter;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A trunk is dialled no faster than its provider accepts; past the limit the call waits, it does not fail. */
class TrunkCallPacerTest {

    private final RateLimiter rateLimiter = mock(RateLimiter.class);

    @Test
    void aTrunkWithoutALimitOfItsOwnIsPacedAtTheDefault() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);

        pacerWithDefault(2).awaitSlot("trunk-endpoint", null);

        verify(rateLimiter).tryAcquire("dialer:cps:trunk-endpoint", 2, Duration.ofSeconds(1));
    }

    @Test
    void theTrunksOwnLimitWins() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);

        pacerWithDefault(2).awaitSlot("trunk_3_9", 10);

        verify(rateLimiter).tryAcquire("dialer:cps:trunk_3_9", 10, Duration.ofSeconds(1));
    }

    @Test
    void aCallOverTheLimitWaitsForTheNextWindow() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(false, false, true);

        pacerWithDefault(2).awaitSlot("trunk-endpoint", null);

        verify(rateLimiter, times(3)).tryAcquire(eq("dialer:cps:trunk-endpoint"), eq(2), any());
    }

    @Test
    void zeroMeansNoLimit() {
        pacerWithDefault(0).awaitSlot("trunk-endpoint", null);

        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), any());
    }

    private TrunkCallPacer pacerWithDefault(int defaultTrunkCps) {
        return new TrunkCallPacer(rateLimiter, new DialerProperties(true, 5, 10, 5, 5, 60, defaultTrunkCps));
    }
}
