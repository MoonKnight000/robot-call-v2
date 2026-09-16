package uz.murodjon.robotcallv2.agent.lifecycle;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shutdown used to tear the last calls down one after another with no limit at all, so one
 * call stuck on a slow CRM held the process for as long as it liked. What matters: the calls
 * run side by side, the wait ends at the deadline whatever is still running, and one failing
 * call does not cost the others their teardown.
 */
class ShutdownTasksTest {

    @Test
    void runsTheTasksSideBySide() {
        AtomicInteger done = new AtomicInteger();
        Runnable slow = () -> {
            sleep(300);
            done.incrementAndGet();
        };

        long started = System.nanoTime();
        int unfinished = ShutdownTasks.runInParallel(List.of(slow, slow, slow), Duration.ofSeconds(5));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(unfinished).isZero();
        assertThat(done.get()).isEqualTo(3);
        assertThat(elapsedMs).isLessThan(800); // one after another would be 900+
    }

    @Test
    void stopsWaitingAtTheDeadline() {
        CountDownLatch never = new CountDownLatch(1);
        Runnable stuck = () -> {
            try {
                never.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        try {
            long started = System.nanoTime();
            int unfinished = ShutdownTasks.runInParallel(List.of(stuck, () -> { }), Duration.ofMillis(200));
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;

            assertThat(unfinished).isEqualTo(1);
            assertThat(elapsedMs).isLessThan(2_000);
        } finally {
            never.countDown();
        }
    }

    @Test
    void aFailingTaskDoesNotCostTheOthersTheirTurn() {
        AtomicInteger done = new AtomicInteger();

        int unfinished = ShutdownTasks.runInParallel(List.of(
                () -> {
                    throw new IllegalStateException("teardown failed");
                },
                done::incrementAndGet), Duration.ofSeconds(5));

        assertThat(unfinished).isZero();
        assertThat(done.get()).isEqualTo(1);
    }

    @Test
    void nothingToRunReturnsAtOnce() {
        assertThat(ShutdownTasks.runInParallel(List.of(), Duration.ofSeconds(5))).isZero();
    }

    @Test
    void awaitTerminationLetsQueuedWorkFinish() {
        ExecutorService lane = Executors.newSingleThreadExecutor();
        AtomicInteger done = new AtomicInteger();
        lane.execute(() -> {
            sleep(100);
            done.incrementAndGet();
        });

        assertThat(ShutdownTasks.awaitTermination(List.of(lane), Duration.ofSeconds(5))).isTrue();
        assertThat(done.get()).isEqualTo(1);
        assertThat(lane.isTerminated()).isTrue();
    }

    @Test
    void awaitTerminationReportsALaneStillBusyAtTheDeadline() {
        ExecutorService idle = Executors.newSingleThreadExecutor();
        ExecutorService busy = Executors.newSingleThreadExecutor();
        CountDownLatch never = new CountDownLatch(1);
        busy.execute(() -> {
            try {
                never.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            assertThat(ShutdownTasks.awaitTermination(List.of(idle, busy), Duration.ofMillis(200))).isFalse();
            assertThat(idle.isShutdown()).isTrue();
            assertThat(busy.isShutdown()).isTrue();
        } finally {
            never.countDown();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
