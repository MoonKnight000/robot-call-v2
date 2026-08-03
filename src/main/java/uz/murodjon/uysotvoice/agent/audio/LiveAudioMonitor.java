package uz.murodjon.uysotvoice.agent.audio;

import java.io.Closeable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mixes one call's two audio directions — the caller and the bot's TTS — into a
 * single live PCM stream for an operator "listen in" (§10.3, UI-DESIGN "Tinglash").
 *
 * <p>Registered as the caller-side {@link AudioListener} in the RTP consumer's normal
 * fan-out list (recording, STT, VAD, ...), so {@link #onAudio} receives the caller's
 * frames the same way those do. The bot's outgoing frames never pass through that
 * fan-out — they are handed to {@link #onBotAudio} directly from {@code
 * RtpEndpoint}'s playback pacer. The two arrive on different threads at roughly the
 * same 20ms cadence, so a scheduled tick mixes whatever each side has queued rather
 * than publishing on either arrival alone — a caller talking over the bot must be
 * audible as both voices at once, not one replacing the other in the output.
 *
 * <p>Several operators may subscribe to the same call at once; each gets its own
 * output queue fed by the same tick, and the tick itself only runs while at least one
 * subscriber is attached.
 */
public class LiveAudioMonitor implements AudioListener, Closeable {

    private static final int FRAME_SAMPLES = 160; // 20ms @ 8kHz — matches RtpEndpoint's own framing
    private static final int TICK_MS = 20;
    private static final int JITTER_FRAMES = 50; // ~1s of buffering before frames are dropped

    /** Published to a subscriber's queue to mark the call as over — no more audio is coming. */
    public static final byte[] EOF = new byte[0];

    private final ScheduledExecutorService scheduler;
    private final BlockingQueue<short[]> callerFrames = new ArrayBlockingQueue<>(JITTER_FRAMES);
    private final BlockingQueue<short[]> botFrames = new ArrayBlockingQueue<>(JITTER_FRAMES);
    private final List<BlockingQueue<byte[]>> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean ticking = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> tick;

    public LiveAudioMonitor(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    /** Caller-side tap — registered as this call's {@link AudioListener}. */
    @Override
    public void onAudio(short[] pcm, int length) {
        offer(callerFrames, Arrays.copyOf(pcm, length));
    }

    /** Bot-side tap — called directly from {@code RtpEndpoint}'s playback pacer. */
    public void onBotAudio(short[] pcm, int length) {
        offer(botFrames, Arrays.copyOf(pcm, length));
    }

    private static void offer(BlockingQueue<short[]> queue, short[] frame) {
        if (!queue.offer(frame)) {
            // Jitter buffer is full — drop the oldest rather than the newest, so a
            // listener that briefly lags catches back up to "now" instead of hearing
            // an ever-growing delay.
            queue.poll();
            queue.offer(frame);
        }
    }

    /** An operator starts listening; starts the mixer tick if this is the first one. */
    public BlockingQueue<byte[]> subscribe() {
        BlockingQueue<byte[]> out = new LinkedBlockingQueue<>(JITTER_FRAMES);
        subscribers.add(out);
        if (ticking.compareAndSet(false, true)) {
            tick = scheduler.scheduleAtFixedRate(this::mixAndPublish, 0, TICK_MS, TimeUnit.MILLISECONDS);
        }
        return out;
    }

    /** An operator stops listening (disconnected, or the call ended). */
    public void unsubscribe(BlockingQueue<byte[]> out) {
        subscribers.remove(out);
        // Last one out stops the tick — otherwise it would keep polling both queues
        // every 20ms for the rest of the call on the shared RTP event loop, for no
        // subscriber to ever receive.
        if (subscribers.isEmpty() && ticking.compareAndSet(true, false)) {
            ScheduledFuture<?> t = tick;
            if (t != null) {
                t.cancel(false);
                tick = null;
            }
            callerFrames.clear();
            botFrames.clear();
        }
    }

    private void mixAndPublish() {
        if (subscribers.isEmpty()) {
            return;
        }
        short[] caller = callerFrames.poll();
        short[] bot = botFrames.poll();
        if (caller == null && bot == null) {
            return; // both sides silent this tick — nothing to publish
        }
        byte[] chunk = mix(caller, bot);
        for (BlockingQueue<byte[]> sub : subscribers) {
            if (!sub.offer(chunk)) {
                sub.poll();
                sub.offer(chunk);
            }
        }
    }

    /** Additive PCM mix of two (possibly absent, possibly short) frames, clamped to 16-bit range. */
    private static byte[] mix(short[] a, short[] b) {
        byte[] out = new byte[FRAME_SAMPLES * 2];
        for (int i = 0; i < FRAME_SAMPLES; i++) {
            int av = a != null && i < a.length ? a[i] : 0;
            int bv = b != null && i < b.length ? b[i] : 0;
            short s = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, av + bv));
            out[i * 2] = (byte) (s & 0xFF);
            out[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return out;
    }

    /** Call teardown — stops the tick and pushes {@link #EOF} to every listening operator. */
    @Override
    public void close() {
        ScheduledFuture<?> t = tick;
        if (t != null) {
            t.cancel(false);
            tick = null;
        }
        for (BlockingQueue<byte[]> sub : subscribers) {
            sub.offer(EOF);
        }
        subscribers.clear();
        callerFrames.clear();
        botFrames.clear();
    }
}
