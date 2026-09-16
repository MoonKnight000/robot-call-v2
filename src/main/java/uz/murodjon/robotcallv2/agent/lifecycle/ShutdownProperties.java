package uz.murodjon.robotcallv2.agent.lifecycle;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How long each step of a shutdown may take. Bound from {@code voice-agent.shutdown.*}; the
 * steps run in this order, from {@code AriService#stop}.
 *
 * @param drainTimeoutSec     how long live calls get to finish on their own while no new call
 *                            is dispatched ({@link GracefulShutdownManager}). 0 skips the wait
 * @param teardownTimeoutSec  how long the calls still up after the drain get, all together, to
 *                            be hung up and torn down — summary, CRM note, recording. They run
 *                            side by side; whatever is not done by then is left to finish on its
 *                            own instead of holding the process. 0 means 30
 * @param eventLaneTimeoutSec how long ARI event handlers already queued get to finish once the
 *                            ARI connection is closed. 0 means 5
 */
@ConfigurationProperties(prefix = "voice-agent.shutdown")
public record ShutdownProperties(
        long drainTimeoutSec,
        long teardownTimeoutSec,
        long eventLaneTimeoutSec
) {

    public ShutdownProperties {
        if (drainTimeoutSec < 0) {
            drainTimeoutSec = 0;
        }
        if (teardownTimeoutSec <= 0) {
            teardownTimeoutSec = 30;
        }
        if (eventLaneTimeoutSec <= 0) {
            eventLaneTimeoutSec = 5;
        }
    }
}
