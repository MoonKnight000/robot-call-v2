package uz.murodjon.robotcallv2.agent.stt;

import java.io.Closeable;

/**
 * An open streaming STT session. Audio is pushed in; transcripts are delivered
 * asynchronously to the {@link TranscriptListener} supplied when the session
 * was started.
 */
public interface SttSession extends Closeable {

    /**
     * Send a chunk of 16-bit little-endian PCM at the session's configured
     * sample rate.
     */
    void sendAudio(byte[] pcm16le);

    /**
     * Tell the recognizer the caller has stopped talking, so it finalizes what it has
     * instead of waiting for its own detector to be sure.
     *
     * <p>Only meaningful on a session opened with external endpointing (see {@link
     * EndpointingProperties}) — a provider whose own detector is in charge ignores this,
     * which is what the default does.
     */
    default void endUtterance() {
    }

    /**
     * Whether this session can still carry audio.
     *
     * <p>A streaming recognizer can be torn down under a live call — the provider's own
     * session limit, a dropped connection, a GOAWAY — and once it is, every further frame
     * goes nowhere. Nothing else about the call breaks, which is what makes it hard to
     * see: the bot simply stops hearing, the silence watchdog asks whether anyone is
     * there, and the call ends as if the caller had gone quiet.
     *
     * <p>{@link SttStreamBridge} polls this and opens a replacement stream, so a provider
     * must flip it as soon as its stream reports an error or completes on its own.
     */
    boolean isAlive();

    /**
     * Whether the transport can take another frame right now.
     *
     * <p>Separate from {@link #isAlive()}: a session stays perfectly alive while it is
     * unable to flush, and every transport underneath queues what it cannot write
     * instead of refusing it — without a bound and without an error. A stalled uplink
     * therefore looks exactly like a caller who says nothing, until the whole call's
     * audio reaches the recognizer at once after the hangup.
     *
     * <p>A provider that cannot tell leaves this {@code true} and behaves as before.
     */
    default boolean isReady() {
        return true;
    }
}
