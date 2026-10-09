package uz.murodjon.robotcallv2.agent.audio;

import ai.onnxruntime.OrtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * One call's neural denoising: 8 kHz frames in, the same number of cleaned 8 kHz samples
 * out, about 45 ms later.
 *
 * <p>The model works on a 16 kHz STFT (512-point frames, 256-sample hop, square-root Hann
 * window — the analysis/synthesis pair GTCRN was trained with), so each frame is upsampled,
 * cut into hops, enhanced, overlap-added back and downsampled. The hop does not divide a
 * 20 ms RTP frame, so output arrives in uneven pieces; a short primed FIFO turns that back
 * into exactly one frame out per frame in, which is what every listener downstream expects.
 *
 * <p>Runs on the endpoint's inbound virtual thread (CLAUDE.md §8.3 — never the Netty loop).
 * Not thread-safe; one per call. If inference ever fails it stops trying and returns
 * {@code null}, and the endpoint falls back to the broadband gate for the rest of the call.
 */
public final class NeuralDenoiseStream {

    private static final Logger log = LoggerFactory.getLogger(NeuralDenoiseStream.class);

    /**
     * Output held back at the start, at 8 kHz: one hop (128) plus the downsampler's
     * lookahead and a margin, so the FIFO never runs dry between uneven hops.
     */
    private static final int PRIMED_SAMPLES = 240;

    private static final int N = NeuralDenoiser.N_FFT;
    private static final int HOP = NeuralDenoiser.HOP;
    private static final int BINS = NeuralDenoiser.BINS;

    private final NeuralDenoiser denoiser;
    private final float[][] caches;
    private final Fft fft = new Fft(N);
    private final double[] window = new double[N];
    private final StreamingUpsampler upsampler = StreamingUpsampler.from8kTo16k();
    private final StreamingDownsampler downsampler = StreamingDownsampler.from16kTo8k();

    /** The last N input samples at 16 kHz; the first half starts as silence, so the first hop is due after HOP samples. */
    private final double[] input = new double[N];
    private int filled = N - HOP;
    private final double[] overlap = new double[N];
    private final double[] re = new double[N];
    private final double[] im = new double[N];

    /** Cleaned 8 kHz audio waiting to be handed out, as a ring. */
    private short[] fifo = new short[2048];
    private int fifoStart;
    private int fifoSize = PRIMED_SAMPLES;

    private boolean failed;

    NeuralDenoiseStream(NeuralDenoiser denoiser, float[][] caches) {
        this.denoiser = denoiser;
        this.caches = caches;
        for (int i = 0; i < N; i++) {
            // Periodic Hann (torch.hann_window's default), square-rooted: applied on both
            // analysis and synthesis it multiplies to Hann, which sums to 1 at 50% overlap.
            window[i] = Math.sqrt(0.5 - 0.5 * Math.cos(2 * Math.PI * i / N));
        }
    }

    /**
     * Cleans one frame. Returns exactly {@code length} samples, or {@code null} once the
     * model has failed — the caller then uses its own fallback.
     */
    public short[] process(short[] pcm, int length) {
        if (failed) {
            return null;
        }
        try {
            short[] wide = upsampler.push(pcm, length);
            short[] cleanedWide = new short[wide.length + HOP];
            int cleanedCount = 0;
            for (short sample : wide) {
                input[filled++] = sample;
                if (filled == N) {
                    if (cleanedCount + HOP > cleanedWide.length) {
                        cleanedWide = Arrays.copyOf(cleanedWide, cleanedWide.length * 2);
                    }
                    hop(cleanedWide, cleanedCount);
                    cleanedCount += HOP;
                    System.arraycopy(input, HOP, input, 0, N - HOP);
                    filled = N - HOP;
                }
            }
            if (cleanedCount > 0) {
                offer(downsampler.push(cleanedWide, cleanedCount));
            }
            return take(length);
        } catch (Exception e) {
            failed = true;
            log.warn("Neural denoiser failed ({}) — this call falls back to the broadband gate", e.getMessage());
            return null;
        }
    }

    /** One STFT hop through the model; writes HOP finished samples at {@code out[offset]}. */
    private void hop(short[] out, int offset) throws OrtException {
        for (int i = 0; i < N; i++) {
            re[i] = input[i] * window[i];
            im[i] = 0;
        }
        fft.forward(re, im);
        float[] frame = new float[BINS * 2];
        for (int k = 0; k < BINS; k++) {
            frame[2 * k] = (float) re[k];
            frame[2 * k + 1] = (float) im[k];
        }
        float[] enhanced = denoiser.enhance(frame, caches);
        for (int k = 0; k < BINS; k++) {
            re[k] = enhanced[2 * k];
            im[k] = enhanced[2 * k + 1];
        }
        for (int k = BINS; k < N; k++) {
            re[k] = re[N - k];
            im[k] = -im[N - k];
        }
        fft.inverse(re, im);
        for (int i = 0; i < N; i++) {
            overlap[i] += re[i] * window[i];
        }
        for (int i = 0; i < HOP; i++) {
            out[offset + i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(overlap[i])));
        }
        System.arraycopy(overlap, HOP, overlap, 0, N - HOP);
        Arrays.fill(overlap, N - HOP, N, 0);
    }

    private void offer(short[] samples) {
        if (fifoSize + samples.length > fifo.length) {
            short[] grown = new short[Math.max(fifo.length * 2, fifoSize + samples.length)];
            for (int i = 0; i < fifoSize; i++) {
                grown[i] = fifo[(fifoStart + i) % fifo.length];
            }
            fifo = grown;
            fifoStart = 0;
        }
        for (short sample : samples) {
            fifo[(fifoStart + fifoSize++) % fifo.length] = sample;
        }
    }

    /** {@code length} samples off the FIFO; silence for any it does not yet hold. */
    private short[] take(int length) {
        short[] out = new short[length];
        int available = Math.min(length, fifoSize);
        for (int i = 0; i < available; i++) {
            out[i] = fifo[(fifoStart + i) % fifo.length];
        }
        fifoStart = (fifoStart + available) % fifo.length;
        fifoSize -= available;
        return out;
    }
}
