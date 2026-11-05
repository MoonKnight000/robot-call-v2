package uz.murodjon.robotcallv2.agent.stt;

import uz.murodjon.robotcallv2.agent.metrics.SttMetrics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Times the recognizer's share of a turn: from the moment this side tells it an utterance is
 * over ({@link SttSession#endUtterance()}) to the final transcript it answers with. One per
 * stream — the session stamps, the response handler settles — recorded as
 * {@code voice.stt.final.latency{provider}}, the same meter Yandex feeds.
 *
 * <p>Only an EOU we sent is measured; a final the provider produced on its own has no moment
 * on this side to measure from. A stamp that was never answered (nothing was said, so no final
 * came) is replaced by the next EOU, and one older than {@link #STALE_AFTER_MS} is dropped
 * rather than recorded as a delay nobody sat through.
 */
final class EouLatencyTracker {

    private static final long STALE_AFTER_MS = 10_000;

    private final AtomicLong sentAt = new AtomicLong();

    /** The end-of-utterance signal has just gone out. */
    void eouSent() {
        sentAt.set(System.nanoTime());
    }

    /** A final transcript arrived; records the delay when an EOU of ours is awaiting it. */
    void finalReceived(SttMetrics metrics, String provider) {
        long stamp = sentAt.getAndSet(0);
        if (stamp == 0) {
            return;
        }
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stamp);
        if (ms <= STALE_AFTER_MS) {
            metrics.sttFinalLatency(provider, ms);
        }
    }
}
