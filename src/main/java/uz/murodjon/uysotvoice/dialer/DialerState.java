package uz.murodjon.uysotvoice.dialer;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks the number of active/reserved outbound calls to enforce
 * {@code max_concurrent_calls} (PROJECT.md §10). A slot is reserved at dispatch and
 * released exactly once when the call ends (or is reclaimed).
 */
@Component
public class DialerState {

    private final AtomicInteger active = new AtomicInteger(0);

    public int active() {
        return active.get();
    }

    public void reserve() {
        active.incrementAndGet();
    }

    public void release() {
        active.updateAndGet(n -> n > 0 ? n - 1 : 0);
    }
}
