package uz.murodjon.uysotvoice.agent.lifecycle;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;

/**
 * Drains active calls before the app stops (PROJECT.md §10 Bosqich 12). On shutdown
 * it flips a draining flag — the dialer stops dispatching new calls — then waits for
 * in-flight calls to finish, up to a timeout, before letting the context tear down.
 */
@Component
public class GracefulShutdownManager {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownManager.class);

    private final VoiceMetrics metrics;
    private final long drainTimeoutSec;
    private volatile boolean draining = false;

    public GracefulShutdownManager(VoiceMetrics metrics,
                                   @Value("${voice-agent.shutdown.drain-timeout-sec:60}") long drainTimeoutSec) {
        this.metrics = metrics;
        this.drainTimeoutSec = drainTimeoutSec;
    }

    public boolean isDraining() {
        return draining;
    }

    @PreDestroy
    public void drain() {
        draining = true;
        int active = metrics.activeCalls();
        if (active == 0) {
            return;
        }
        log.info("Shutdown: draining {} active call(s), up to {}s", active, drainTimeoutSec);
        long deadline = System.nanoTime() + drainTimeoutSec * 1_000_000_000L;
        while (metrics.activeCalls() > 0 && System.nanoTime() < deadline) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        int remaining = metrics.activeCalls();
        if (remaining > 0) {
            log.warn("Shutdown: {} call(s) still active after drain timeout", remaining);
        } else {
            log.info("Shutdown: all calls drained");
        }
    }
}
