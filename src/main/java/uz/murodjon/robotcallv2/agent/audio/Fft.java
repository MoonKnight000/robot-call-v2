package uz.murodjon.robotcallv2.agent.audio;

/**
 * In-place iterative radix-2 complex FFT, for the short fixed sizes the neural denoiser's
 * STFT needs (512 points). Unnormalized forward, {@code 1/n} on the inverse — the same
 * convention as {@code torch.stft} / {@code torch.istft}, which the model was trained with.
 */
final class Fft {

    private final int n;
    private final double[] cos;
    private final double[] sin;
    private final int[] reversed;

    Fft(int n) {
        if (Integer.bitCount(n) != 1) {
            throw new IllegalStateException("FFT size must be a power of two: " + n);
        }
        this.n = n;
        this.cos = new double[n / 2];
        this.sin = new double[n / 2];
        for (int i = 0; i < n / 2; i++) {
            cos[i] = Math.cos(2 * Math.PI * i / n);
            sin[i] = Math.sin(2 * Math.PI * i / n);
        }
        this.reversed = new int[n];
        int bits = Integer.numberOfTrailingZeros(n);
        for (int i = 0; i < n; i++) {
            reversed[i] = Integer.reverse(i) >>> (32 - bits);
        }
    }

    void forward(double[] re, double[] im) {
        transform(re, im, -1);
    }

    void inverse(double[] re, double[] im) {
        transform(re, im, 1);
        for (int i = 0; i < n; i++) {
            re[i] /= n;
            im[i] /= n;
        }
    }

    /** {@code sign} -1 is the forward kernel e^(-i·2πkn/N), +1 the inverse. */
    private void transform(double[] re, double[] im, int sign) {
        for (int i = 0; i < n; i++) {
            int j = reversed[i];
            if (j > i) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int size = 2; size <= n; size <<= 1) {
            int half = size >> 1;
            int step = n / size;
            for (int start = 0; start < n; start += size) {
                for (int k = 0; k < half; k++) {
                    double wr = cos[k * step];
                    double wi = sign * sin[k * step];
                    int a = start + k;
                    int b = a + half;
                    double xr = re[b] * wr - im[b] * wi;
                    double xi = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                }
            }
        }
    }
}
