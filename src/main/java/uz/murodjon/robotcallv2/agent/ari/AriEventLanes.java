package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.lifecycle.ShutdownTasks;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ARI events are handled here, not on ari4java's websocket reader thread: a handler
 * waiting on the database or on an ARI REST call used to hold up every later event
 * of every call — a StasisStart queued behind a slow write was dead air. One serial
 * lane per channel (by hash) keeps each channel's own events in the order Asterisk sent
 * them: ChannelHangupRequest, then StasisEnd, then ChannelDestroyed.
 *
 * <p>One lane array for everything that has to stay in a channel's event order — the
 * recognizer's final transcript lines are queued on the same lane, ahead of the StasisEnd
 * whose teardown finalizes the call. No {@code @PreDestroy}: {@link AriService#stop} shuts
 * the lanes down last, after the calls and the ARI connection.
 */
@Component
class AriEventLanes {

    private static final Logger log = LoggerFactory.getLogger(AriEventLanes.class);

    private static final int LANE_COUNT = 16;

    private final ExecutorService[] eventLanes = createEventLanes(LANE_COUNT);

    /** Runs {@code handler} on {@code channelId}'s lane. */
    void execute(String channelId, Runnable handler) {
        int lane = Math.floorMod(channelId != null ? channelId.hashCode() : 0, eventLanes.length);
        eventLanes[lane].execute(() -> {
            try {
                handler.run();
            } catch (Exception e) {
                log.error("ARI event handler failed for channel {}: {}", channelId, e.getMessage(), e);
            }
        });
    }

    /**
     * Stops the lanes taking new events and waits, up to {@code timeout}, for what is already
     * queued on them.
     *
     * @return whether every lane finished in time
     */
    boolean shutdown(Duration timeout) {
        return ShutdownTasks.awaitTermination(List.of(eventLanes), timeout);
    }

    private static ExecutorService[] createEventLanes(int count) {
        ExecutorService[] lanes = new ExecutorService[count];
        for (int i = 0; i < count; i++) {
            lanes[i] = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("ari-event-" + i).factory());
        }
        return lanes;
    }
}
