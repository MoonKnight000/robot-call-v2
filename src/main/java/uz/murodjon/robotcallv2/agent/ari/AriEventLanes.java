package uz.murodjon.robotcallv2.agent.ari;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.lifecycle.ShutdownTasks;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ARI events run away from ari4java's websocket reader thread. A separate serial queue per
 * channel keeps a slow handler from delaying unrelated calls while preserving event order for
 * each channel. Recognizer final transcripts use the same queue, ahead of StasisEnd teardown.
 * {@link AriService#stop} shuts the executor down last, after calls and the ARI connection.
 */
@Component
class AriEventLanes {

    private static final Logger log = LoggerFactory.getLogger(AriEventLanes.class);
    private static final int QUEUE_WARNING_THRESHOLD = 128;

    private final ExecutorService eventExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<String, ChannelLane> channelLanes = new ConcurrentHashMap<>();
    private final AtomicInteger pendingEvents = new AtomicInteger();
    private final Timer queueWait;
    private final Timer handlerDuration;

    AriEventLanes(MeterRegistry meterRegistry) {
        Gauge.builder("asterisk.ari.events.pending", pendingEvents, AtomicInteger::get)
                .description("ARI channel events queued or being handled")
                .register(meterRegistry);
        queueWait = Timer.builder("asterisk.ari.events.queue.wait")
                .description("time ARI events wait before their channel handler starts")
                .publishPercentileHistogram()
                .register(meterRegistry);
        handlerDuration = Timer.builder("asterisk.ari.events.handler.duration")
                .description("duration of ARI channel event handlers")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    /** Runs {@code handler} on {@code channelId}'s serial queue. */
    void execute(String channelId, Runnable handler) {
        String key = channelId != null ? channelId : "";
        long enqueuedAt = System.nanoTime();
        int pending = pendingEvents.incrementAndGet();
        if (pending == QUEUE_WARNING_THRESHOLD + 1) {
            log.warn("ARI event backlog exceeded {} pending events", QUEUE_WARNING_THRESHOLD);
        }
        try {
            channelLanes.compute(key, (ignored, lane) -> {
                ChannelLane selected = lane != null ? lane : new ChannelLane(key);
                selected.execute(() -> {
                    queueWait.record(System.nanoTime() - enqueuedAt, TimeUnit.NANOSECONDS);
                    long startedAt = System.nanoTime();
                    try {
                        handler.run();
                    } catch (Exception e) {
                        log.error("ARI event handler failed for channel {}: {}", channelId, e.getMessage(), e);
                    } finally {
                        handlerDuration.record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
                        int remaining = pendingEvents.decrementAndGet();
                        if (remaining == QUEUE_WARNING_THRESHOLD) {
                            log.info("ARI event backlog returned below {} pending events", QUEUE_WARNING_THRESHOLD + 1);
                        }
                    }
                });
                return selected;
            });
        } catch (RuntimeException e) {
            pendingEvents.decrementAndGet();
            throw e;
        }
    }

    /** Stops new work and waits, up to {@code timeout}, for queued events to finish. */
    boolean shutdown(Duration timeout) {
        return ShutdownTasks.awaitTermination(List.of(eventExecutor), timeout);
    }

    private void drain(String key, ChannelLane lane) {
        while (true) {
            Runnable task;
            synchronized (lane) {
                task = lane.tasks.pollFirst();
                if (task == null) {
                    lane.running = false;
                }
            }
            if (task == null) {
                channelLanes.compute(key, (ignored, current) -> {
                    synchronized (lane) {
                        return current == lane && !lane.running && lane.tasks.isEmpty() ? null : current;
                    }
                });
                return;
            }
            task.run();
        }
    }

    private final class ChannelLane {

        private final String key;
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private boolean running;

        private ChannelLane(String key) {
            this.key = key;
        }

        private synchronized void execute(Runnable task) {
            tasks.addLast(task);
            if (running) {
                return;
            }
            running = true;
            try {
                eventExecutor.execute(() -> drain(key, this));
            } catch (RuntimeException e) {
                tasks.removeLastOccurrence(task);
                running = false;
                throw e;
            }
        }
    }
}
