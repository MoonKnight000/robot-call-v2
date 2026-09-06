package uz.murodjon.robotcallv2.agent.audio;

import java.util.Arrays;

/**
 * {@link Resampler}'s anti-aliased decimation for audio that arrives in chunks.
 *
 * <p>The one-shot functions in {@link Resampler} treat each array as a whole signal: at
 * both edges the FIR filter clamps to the first/last sample, and a chunk whose length is
 * not a multiple of the factor drops its remainder. Fed the 40 ms chunks a streaming
 * synthesizer produces, that put a filter discontinuity — a small click — and a phase
 * jump at every chunk boundary, i.e. twenty-five times a second, which is heard as a
 * grainy, "cheap" voice. This keeps the filter history across calls so the boundaries
 * are invisible: the output is the same as resampling the whole utterance at once.
 *
 * <p>Not thread-safe; one instance per audio stream.
 */
public final class StreamingDownsampler {

    private final int[] fir;
    private final int offset;
    private final int factor;
    /** Ring of the last {@code fir.length} input samples. */
    private final short[] history;
    /** Input samples received so far. */
    private long received;
    /** Input index of the next output's filter centre. */
    private long nextCenter;

    private StreamingDownsampler(int[] fir, int offset, int factor, int firstCenter) {
        this.fir = fir;
        this.offset = offset;
        this.factor = factor;
        this.history = new short[fir.length];
        this.nextCenter = firstCenter;
    }

    /** The same filter as {@link Resampler#downsample24kTo8k}, applied continuously. */
    public static StreamingDownsampler from24kTo8k() {
        return new StreamingDownsampler(Resampler.FIR_24K_TO_8K, Resampler.FIR_24K_OFFSET, 3, 1);
    }

    /** The same filter as {@link Resampler#downsample16kTo8k}, applied continuously. */
    public static StreamingDownsampler from16kTo8k() {
        return new StreamingDownsampler(Resampler.FIR_16K_TO_8K, Resampler.FIR_16K_OFFSET, 2, 0);
    }

    /**
     * Feed the next samples and get back every output sample they complete. The filter
     * looks a few samples ahead, so the last few inputs are held until the next call (or
     * {@link #flush()}).
     */
    public short[] push(short[] in, int length) {
        if (in == null || length <= 0) {
            return new short[0];
        }
        int lookahead = fir.length - 1 - offset;
        short[] out = new short[length / factor + 2];
        int produced = 0;
        for (int i = 0; i < length; i++) {
            history[(int) (received % history.length)] = in[i];
            received++;
            // An output is computable once the last sample its filter needs has arrived.
            while (nextCenter + lookahead < received) {
                int acc = 0;
                for (int k = 0; k < fir.length; k++) {
                    long idx = nextCenter + k - offset;
                    if (idx < 0) {
                        idx = 0; // before the stream started: clamp, as the one-shot filter does
                    }
                    acc += fir[k] * history[(int) (idx % history.length)];
                }
                int sample = (acc + 16384) >> 15;
                if (produced == out.length) {
                    out = Arrays.copyOf(out, out.length * 2);
                }
                out[produced++] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
                nextCenter += factor;
            }
        }
        return produced == out.length ? out : Arrays.copyOf(out, produced);
    }

    /** Release the tail the lookahead was holding — the stream has ended. */
    public short[] flush() {
        int lookahead = fir.length - 1 - offset;
        if (received == 0 || lookahead == 0) {
            return new short[0];
        }
        // Pad with the last sample, which is what the one-shot filter clamps to at the end.
        short last = history[(int) ((received - 1) % history.length)];
        short[] pad = new short[lookahead];
        Arrays.fill(pad, last);
        return push(pad, pad.length);
    }
}
