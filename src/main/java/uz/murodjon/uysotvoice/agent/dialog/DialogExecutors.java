package uz.murodjon.uysotvoice.agent.dialog;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The two threading resources every stage of the cascade pipeline shares: a
 * virtual-thread worker for anything that blocks, and one scheduler thread for anything
 * that has to happen later.
 *
 * <p>They are here rather than in each stage because the rule that governs them is a
 * single rule, and splitting it across four classes is how it gets broken: <b>the
 * scheduler thread must never block.</b> It drives every call's silence watchdog and
 * every turn's filler at once, so one TTS round trip or one hangup on it stalls the
 * timing of every other live call. That is what {@link #scheduleOnWorker} exists for —
 * the scheduler only decides, the worker does the work.
 *
 * <p>The worker is a virtual-thread-per-task executor: a turn spends nearly all of its
 * time waiting on the LLM and the TTS, and a platform-thread pool sized for that would
 * either cap concurrent calls or sit idle.
 */
@Component
public class DialogExecutors {

    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dialog-watchdog");
                t.setDaemon(true);
                return t;
            });

    /** Run {@code task} on the virtual-thread worker — where blocking calls belong. */
    public void submit(Runnable task) {
        worker.submit(task);
    }

    /**
     * Run {@code task} on the worker after {@code delayMs}. The scheduler thread only
     * hands it over, so a task that blocks delays nothing but itself.
     *
     * @return the scheduled handle, so a caller that no longer wants the task can cancel it
     */
    public ScheduledFuture<?> scheduleOnWorker(Runnable task, long delayMs) {
        return scheduler.schedule(() -> worker.submit(task), delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Run {@code tick} on the scheduler thread every {@code period}, deliberately not on
     * the worker: a tick that only reads a few flags is cheaper here than a task handed
     * over once a second for the whole length of every call.
     */
    public ScheduledFuture<?> scheduleTicks(Runnable tick, Duration period) {
        return scheduler.scheduleWithFixedDelay(tick, period.toMillis(), period.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        worker.shutdownNow();
    }
}
