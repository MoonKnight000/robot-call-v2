package uz.murodjon.robotcallv2.agent.tts;

/**
 * Guards one blocking, streamed synthesis call against a provider that never produces audio.
 * Created on the calling thread; if {@link #firstAudioArrived()} has not been called within the
 * timeout the calling thread is interrupted, which turns the provider's blocking read into an
 * exception the router already handles by falling back.
 *
 * <p>{@link #close()} must run before the caller does anything else blocking: when the guard
 * fired it clears the interrupt, so the fallback call is not itself cut short. The interrupt
 * and the close take the same lock, so a late firing can never leave a stray interrupt on the
 * thread after {@code close()} returned.
 */
final class FirstChunkWatchdog implements AutoCloseable {

    private static final FirstChunkWatchdog DISABLED = new FirstChunkWatchdog();

    private final Thread caller;
    private final Thread timer;
    private boolean done;
    private boolean timedOut;

    private FirstChunkWatchdog() {
        this.caller = null;
        this.timer = null;
    }

    private FirstChunkWatchdog(long timeoutMs) {
        this.caller = Thread.currentThread();
        this.timer = Thread.ofVirtual().name("tts-first-chunk-watchdog").unstarted(() -> {
            try {
                Thread.sleep(timeoutMs);
            } catch (InterruptedException e) {
                return;
            }
            synchronized (this) {
                if (!done) {
                    timedOut = true;
                    caller.interrupt();
                }
            }
        });
    }

    /** Starts guarding the current thread; {@code timeoutMs <= 0} guards nothing. */
    static FirstChunkWatchdog start(long timeoutMs) {
        if (timeoutMs <= 0) {
            return DISABLED;
        }
        FirstChunkWatchdog watchdog = new FirstChunkWatchdog(timeoutMs);
        watchdog.timer.start();
        return watchdog;
    }

    /** The provider delivered audio: the guard has done its job. */
    synchronized void firstAudioArrived() {
        done = true;
    }

    /** Whether the guard fired — the provider produced no audio in time. */
    synchronized boolean timedOut() {
        return timedOut;
    }

    /** Stops the guard; if it had fired, clears the interrupt it left on the calling thread. */
    @Override
    public void close() {
        if (timer == null) {
            return;
        }
        boolean fired;
        synchronized (this) {
            done = true;
            fired = timedOut;
        }
        timer.interrupt();
        if (fired) {
            Thread.interrupted();
        }
    }
}
