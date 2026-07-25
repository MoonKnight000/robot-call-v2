package uz.murodjon.uysotvoice.agent.stt;

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
}
