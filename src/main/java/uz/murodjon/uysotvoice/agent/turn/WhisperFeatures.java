package uz.murodjon.uysotvoice.agent.turn;

/**
 * The log-mel spectrogram a Whisper encoder expects, computed in Java.
 *
 * <p>Smart Turn v3 is a Whisper Tiny encoder with a classifier on top, and its ONNX graph
 * starts at {@code input_features} — the mel spectrogram, not the audio. In Python that
 * step is one line of {@code transformers}; here it has to exist, because the whole point
 * of the exercise is not to run a Python process next to the JVM (§8.1).
 *
 * <p>This reproduces {@code WhisperFeatureExtractor} exactly: a Hann-windowed STFT with
 * reflect padding on both ends, power spectrum, a Slaney-scaled mel filterbank, log10,
 * and the two normalizations Whisper applies afterwards — an 8-decade floor relative to
 * the loudest bin, then a shift into roughly [-1, 1].
 *
 * <p>The defaults ({@code nFft} 512, {@code hop} 256, 128 mels) are the ones that produce
 * the 500 frames Smart Turn v3 declares for 8 seconds of 16 kHz audio: reflect padding
 * adds {@code nFft} samples, so {@code 128000 / 256} frames come out once Whisper drops
 * the trailing one. They are settings rather than constants because a re-export with a
 * different preprocessor config would change them, and a mismatch here is invisible —
 * the model returns a number either way, just not a meaningful one.
 * {@link SmartTurnDetector} checks the shape against the graph for that reason.
 *
 * <p>{@code nFft} must be a power of two: the transform below is a plain radix-2 FFT, and
 * an arbitrary size would need Bluestein's algorithm to stay exact. Whisper's own 400 is
 * not a power of two, so a model exported with the stock config cannot be served here —
 * which the detector says out loud rather than silently returning noise.
 *
 * <p>Not thread-safe: the scratch buffers are reused between calls. One instance belongs
 * to the single detector that owns it.
 */
final class WhisperFeatures {

    /** Whisper's floor on the mel energies before the log — keeps silence finite. */
    private static final double LOG_FLOOR = 1e-10;
    /** Decades below the loudest bin that are flattened away. */
    private static final double DYNAMIC_RANGE = 8.0;

    private final int nFft;
    private final int hop;
    private final int nMels;
    private final int frames;
    private final int bins;
    private final int samples;

    private final double[] window;
    private final double[][] melFilters;

    // Reused scratch: one frame's real and imaginary parts, and one frame's power spectrum.
    private final double[] re;
    private final double[] im;
    private final double[] power;

    WhisperFeatures(int sampleRate, int nFft, int hop, int nMels, int frames) {
        if (Integer.bitCount(nFft) != 1) {
            throw new IllegalArgumentException("nFft must be a power of two, was " + nFft);
        }
        this.nFft = nFft;
        this.hop = hop;
        this.nMels = nMels;
        this.frames = frames;
        this.bins = nFft / 2 + 1;
        this.samples = frames * hop;
        this.window = hann(nFft);
        this.melFilters = melFilterbank(sampleRate, nFft, nMels);
        this.re = new double[nFft];
        this.im = new double[nFft];
        this.power = new double[bins];
    }

    /** How much 16 kHz audio one call consumes; anything shorter is padded with silence. */
    int samples() {
        return samples;
    }

    int nMels() {
        return nMels;
    }

    int frames() {
        return frames;
    }

    /**
     * The spectrogram of the last {@link #samples()} samples of {@code pcm}, flattened
     * mel-major to match the model's {@code (1, nMels, frames)} input.
     *
     * <p>Shorter input is padded with silence on the right rather than the left, which is
     * both what the reference does and what the situation is: the caller stopped talking,
     * and what follows their last word is silence.
     */
    float[] extract(short[] pcm, int length) {
        double[] padded = pad(pcm, length);
        float[] out = new float[nMels * frames];
        double loudest = Double.NEGATIVE_INFINITY;

        for (int frame = 0; frame < frames; frame++) {
            powerSpectrum(padded, frame * hop);
            for (int mel = 0; mel < nMels; mel++) {
                double[] filter = melFilters[mel];
                double sum = 0;
                for (int bin = 0; bin < bins; bin++) {
                    sum += filter[bin] * power[bin];
                }
                double log = Math.log10(Math.max(sum, LOG_FLOOR));
                loudest = Math.max(loudest, log);
                out[mel * frames + frame] = (float) log;
            }
        }

        // Whisper's two normalizations, in its order: flatten everything more than eight
        // decades below the loudest bin, then shift the result into roughly [-1, 1].
        float floor = (float) (loudest - DYNAMIC_RANGE);
        for (int i = 0; i < out.length; i++) {
            out[i] = (Math.max(out[i], floor) + 4f) / 4f;
        }
        return out;
    }

    /**
     * The last {@link #samples()} samples, scaled to [-1, 1] and reflect-padded by half a
     * window at each end — {@code center=True}, without which the first and last frames
     * would be windowed against nothing.
     */
    private double[] pad(short[] pcm, int length) {
        int half = nFft / 2;
        double[] padded = new double[samples + nFft];
        int available = Math.min(length, samples);
        int start = length - available;
        for (int i = 0; i < available; i++) {
            padded[half + i] = pcm[start + i] / 32768.0;
        }
        // Reflect, excluding the edge sample itself, as numpy and torch both do.
        for (int i = 0; i < half; i++) {
            padded[half - 1 - i] = padded[half + 1 + i];
            int tail = half + samples + i;
            padded[tail] = padded[tail - 2 - 2 * i];
        }
        return padded;
    }

    /** One frame's |FFT|² into {@link #power}. */
    private void powerSpectrum(double[] signal, int offset) {
        for (int i = 0; i < nFft; i++) {
            re[i] = signal[offset + i] * window[i];
            im[i] = 0;
        }
        fft(re, im);
        for (int bin = 0; bin < bins; bin++) {
            power[bin] = re[bin] * re[bin] + im[bin] * im[bin];
        }
    }

    /** In-place iterative radix-2 Cooley-Tukey. */
    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double tr = re[i];
                re[i] = re[j];
                re[j] = tr;
                double ti = im[i];
                im[i] = im[j];
                im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            double wRe = Math.cos(angle);
            double wIm = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double curRe = 1;
                double curIm = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k;
                    int b = a + len / 2;
                    double evenRe = re[a];
                    double evenIm = im[a];
                    double oddRe = re[b] * curRe - im[b] * curIm;
                    double oddIm = re[b] * curIm + im[b] * curRe;
                    re[a] = evenRe + oddRe;
                    im[a] = evenIm + oddIm;
                    re[b] = evenRe - oddRe;
                    im[b] = evenIm - oddIm;
                    double nextRe = curRe * wRe - curIm * wIm;
                    curIm = curRe * wIm + curIm * wRe;
                    curRe = nextRe;
                }
            }
        }
    }

    /** Periodic Hann, which is what torch produces by default and what Whisper assumes. */
    private static double[] hann(int size) {
        double[] w = new double[size];
        for (int i = 0; i < size; i++) {
            w[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / size);
        }
        return w;
    }

    /**
     * Triangular mel filters on the Slaney scale, area-normalized — {@code mel_scale} and
     * {@code norm} both "slaney", which is what Whisper was trained with. The HTK formula
     * is the other common convention and produces visibly different features.
     */
    private static double[][] melFilterbank(int sampleRate, int nFft, int nMels) {
        int bins = nFft / 2 + 1;
        double[] edges = new double[nMels + 2];
        double maxMel = hzToMel(sampleRate / 2.0);
        for (int i = 0; i < edges.length; i++) {
            edges[i] = melToHz(maxMel * i / (nMels + 1));
        }
        double[][] filters = new double[nMels][bins];
        for (int mel = 0; mel < nMels; mel++) {
            double lower = edges[mel];
            double centre = edges[mel + 1];
            double upper = edges[mel + 2];
            double area = 2.0 / (upper - lower);
            for (int bin = 0; bin < bins; bin++) {
                double hz = (double) bin * sampleRate / nFft;
                double rising = (hz - lower) / (centre - lower);
                double falling = (upper - hz) / (upper - centre);
                filters[mel][bin] = Math.max(0, Math.min(rising, falling)) * area;
            }
        }
        return filters;
    }

    // Slaney: linear below 1 kHz, logarithmic above, meeting at 15 mels.
    private static final double LINEAR_SLOPE = 200.0 / 3.0;
    private static final double BREAK_HZ = 1000.0;
    private static final double BREAK_MEL = BREAK_HZ / LINEAR_SLOPE;
    private static final double LOG_STEP = Math.log(6.4) / 27.0;

    private static double hzToMel(double hz) {
        return hz < BREAK_HZ ? hz / LINEAR_SLOPE : BREAK_MEL + Math.log(hz / BREAK_HZ) / LOG_STEP;
    }

    private static double melToHz(double mel) {
        return mel < BREAK_MEL ? mel * LINEAR_SLOPE : BREAK_HZ * Math.exp(LOG_STEP * (mel - BREAK_MEL));
    }
}
