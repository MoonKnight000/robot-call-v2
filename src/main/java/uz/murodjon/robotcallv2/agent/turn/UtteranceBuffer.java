package uz.murodjon.robotcallv2.agent.turn;

import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.audio.Resampler;

/**
 * The last few seconds of one call's inbound audio, kept so the end of an utterance can be
 * scored when the wait for the caller runs out.
 *
 * <p>A ring rather than a growing buffer: {@link SmartTurnDetector} looks at a fixed
 * window ending at the caller's last word, and everything before it is of no interest.
 * Samples are held at the line's own 8 kHz and upsampled only when a score is actually
 * asked for — that happens once per utterance, against forty frames a second arriving.
 *
 * <p>Not thread-safe, and does not need to be: it is an {@code AudioListener}, so writes
 * come from that call's single RTP consumer thread, and the read is made from the same
 * thread by the gate sitting further down the same listener chain.
 */
public class UtteranceBuffer implements AudioListener {

    /** The line's rate; {@link SmartTurnDetector#SAMPLE_RATE} is twice this. */
    private static final int SOURCE_RATE = SmartTurnDetector.SAMPLE_RATE / 2;

    private final short[] ring;
    private int writePos;
    private int filled;

    /**
     * @param samples16k how much 16 kHz audio the detector scores; half that is held here
     */
    public UtteranceBuffer(int samples16k) {
        this.ring = new short[Math.max(1, samples16k / 2)];
    }

    @Override
    public void onAudio(short[] pcm, int length) {
        int count = Math.min(length, ring.length);
        // Only the tail can survive the wrap, so skip straight to it.
        int start = length - count;
        for (int i = 0; i < count; i++) {
            ring[writePos] = pcm[start + i];
            writePos = (writePos + 1) % ring.length;
        }
        filled = Math.min(filled + count, ring.length);
    }

    /** How many 16 kHz samples {@link #recent()} would return. */
    public int length() {
        return filled * 2;
    }

    /**
     * Everything held, oldest first, at the rate the model expects. The buffer is left
     * intact: the same audio is still the run-up to whatever the caller says next.
     */
    public short[] recent() {
        short[] out = new short[filled];
        int start = (writePos - filled + ring.length) % ring.length;
        for (int i = 0; i < filled; i++) {
            out[i] = ring[(start + i) % ring.length];
        }
        return Resampler.upsample8kTo16k(out, out.length);
    }

    /** The line rate this buffer assumes, so a caller can check it matches the call's. */
    public static int sourceRate() {
        return SOURCE_RATE;
    }
}
