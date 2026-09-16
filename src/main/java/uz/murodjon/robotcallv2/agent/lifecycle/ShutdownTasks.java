package uz.murodjon.robotcallv2.agent.lifecycle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The waits of a shutdown that must not be open-ended: the last calls' teardown, and the
 * executors finishing what was already queued on them. Each waits up to one deadline shared
 * by everything it waits for, then reports what was left — instead of holding the process for
 * as long as the slowest call happens to take.
 */
public final class ShutdownTasks {

    private ShutdownTasks() {
    }

    /**
     * Runs every task at once, each on its own virtual thread, and waits for them until
     * {@code timeout} has passed. A task still running then is left to finish on its own —
     * not interrupted, because half a teardown (a summary saved without its CRM note) is worse
     * than a late one. A task that throws counts as finished; logging its failure is its own job.
     *
     * @return how many tasks had not finished by the deadline
     */
    public static int runInParallel(List<Runnable> tasks, Duration timeout) {
        if (tasks.isEmpty()) {
            return 0;
        }
        ExecutorService executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("shutdown-task-", 0).factory());
        List<Future<?>> futures = new ArrayList<>(tasks.size());
        for (Runnable task : tasks) {
            futures.add(executor.submit(task));
        }
        executor.shutdown(); // takes no more work; leaves the running tasks alone
        long deadline = System.nanoTime() + timeout.toNanos();
        for (Future<?> future : futures) {
            try {
                future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException | ExecutionException e) {
                // Late, or failed: either way there is nothing more to wait for on this one.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return (int) futures.stream().filter(future -> !future.isDone()).count();
    }

    /**
     * Stops every executor taking new work, then waits — up to {@code timeout} for all of
     * them together — for what is already queued on them to finish.
     *
     * @return whether every executor finished in time
     */
    public static boolean awaitTermination(List<ExecutorService> executors, Duration timeout) {
        executors.forEach(ExecutorService::shutdown);
        long deadline = System.nanoTime() + timeout.toNanos();
        for (ExecutorService executor : executors) {
            try {
                if (!executor.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
