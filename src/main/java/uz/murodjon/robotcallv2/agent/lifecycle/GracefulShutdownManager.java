package uz.murodjon.robotcallv2.agent.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.agent.metrics.CallMetrics;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drains active calls before the app stops (PROJECT.md §10 Bosqich 12). On shutdown
 * it flips a draining flag — the dialer stops dispatching new calls — then waits for
 * in-flight calls to finish, up to a timeout.
 *
 * <p>Called from {@code AriService#stop}, the first step of its shutdown phase, and nowhere
 * else. It used to be a {@code @PreDestroy} of its own as well, so it ran a second time
 * after the calls had already been torn down; now it runs once, and a second call returns
 * at once.
 */
@Component
public class GracefulShutdownManager {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownManager.class);

    private final CallMetrics metrics;
    private final ShutdownProperties shutdownProperties;
    private final AtomicBoolean drained = new AtomicBoolean();
    private volatile boolean draining = false;

    public GracefulShutdownManager(CallMetrics metrics, ShutdownProperties shutdownProperties) {
        this.metrics = metrics;
        this.shutdownProperties = shutdownProperties;
    }

    public boolean isDraining() {
        return draining;
    }

    public void drain() {
        draining = true;
        if (!drained.compareAndSet(false, true)) {
            return;
        }
        int active = metrics.activeCalls();
        if (active == 0) {
            return;
        }
        long drainTimeoutSec = shutdownProperties.drainTimeoutSec();
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
