package uz.murodjon.robotcallv2.agent.vad;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TEN VAD — the voice-activity model of the TEN framework — loaded from its native library.
 * The library is shared; each stream gets its own handle, since the model keeps state
 * between frames ({@link #openStream}).
 *
 * <p>Non-fatal like {@link SileroVad}: a missing library or a failed load leave it
 * unavailable, and calls fall back to Silero.
 */
@Component
@ConditionalOnProperty(prefix = "voice-agent.ten-vad", name = "enabled", havingValue = "true")
public class TenVad {

    private static final Logger log = LoggerFactory.getLogger(TenVad.class);

    /** The only rate TEN VAD accepts. */
    private static final int TEN_SAMPLE_RATE = 16000;

    private final TenVadProperties tenVadProperties;
    private final VadProperties vadProperties;
    private volatile TenVadLibrary library;

    public TenVad(TenVadProperties tenVadProperties, VadProperties vadProperties) {
        this.tenVadProperties = tenVadProperties;
        this.vadProperties = vadProperties;
    }

    @PostConstruct
    public void init() {
        String path = tenVadProperties.libraryPath();
        if (path == null || path.isBlank() || !Files.exists(Path.of(path))) {
            log.error("TEN VAD library not found at {} (voice-agent.ten-vad.library-path); calls keep Silero", path);
            return;
        }
        int rate = vadProperties.sampleRate();
        int upsampled = vadProperties.windowSamples() * (TEN_SAMPLE_RATE / rate);
        if ((rate != 8000 && rate != TEN_SAMPLE_RATE) || tenVadProperties.hopSize() <= 0
                || upsampled % tenVadProperties.hopSize() != 0) {
            log.error("TEN VAD needs 8 or 16 kHz windows that make whole {}-sample hops at 16 kHz "
                    + "(got {} samples at {} Hz); calls keep Silero", tenVadProperties.hopSize(),
                    vadProperties.windowSamples(), rate);
            return;
        }
        try {
            TenVadLibrary loaded = Native.load(Path.of(path).toAbsolutePath().toString(), TenVadLibrary.class);
            log.info("TEN VAD ready (library={}, version={}, hop={})",
                    path, loaded.ten_vad_get_version(), tenVadProperties.hopSize());
            library = loaded;
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            log.error("Failed to load the TEN VAD library {}: {}", path, e.getMessage());
        }
    }

    public boolean available() {
        return library != null;
    }

    /** A scorer with its own native handle, or null when one could not be created. */
    public VadScorer openStream() {
        TenVadLibrary current = library;
        if (current == null) {
            return null;
        }
        PointerByReference handle = new PointerByReference();
        if (current.ten_vad_create(handle, tenVadProperties.hopSize(), tenVadProperties.threshold()) != 0) {
            log.warn("TEN VAD handle could not be created");
            return null;
        }
        return new TenVadStream(current, handle.getValue(), TEN_SAMPLE_RATE / vadProperties.sampleRate(),
                tenVadProperties.hopSize(), tenVadProperties.threshold(), tenVadProperties.listeningThreshold());
    }

    /**
     * One stream's handle. Scored from the call's RTP thread and closed from teardown, hence
     * the lock: a frame scored on a destroyed handle would be a native use-after-free.
     */
    private static final class TenVadStream implements VadScorer {

        private final TenVadLibrary library;
        private final int factor;
        private final int hopSize;
        private final float bargeInThreshold;
        private final float listeningThreshold;
        private final FloatByReference probability = new FloatByReference();
        private final IntByReference flag = new IntByReference();
        private short[] upsampled = new short[0];
        private final short[] hop;
        private Pointer handle;
        /** The last sample of the window before, so interpolation runs across window edges. */
        private float previous;

        private TenVadStream(TenVadLibrary library, Pointer handle, int factor, int hopSize,
                             float bargeInThreshold, float listeningThreshold) {
            this.bargeInThreshold = bargeInThreshold;
            this.listeningThreshold = listeningThreshold;
            this.library = library;
            this.handle = handle;
            this.factor = factor;
            this.hopSize = hopSize;
            this.hop = new short[hopSize];
        }

        /**
         * The mean probability of the window's 16 kHz hops — one score per window, like Silero's.
         * The mean, not the highest: one hop of a breath scoring high was enough to make the whole
         * window speech.
         */
        @Override
        public synchronized float score(float[] window) {
            if (handle == null) {
                return -1f;
            }
            upsample(window);
            float sum = 0f;
            int hops = 0;
            for (int offset = 0; offset < upsampled.length; offset += hopSize) {
                System.arraycopy(upsampled, offset, hop, 0, hopSize);
                if (library.ten_vad_process(handle, hop, hopSize, probability, flag) != 0) {
                    return -1f;
                }
                sum += probability.getValue();
                hops++;
            }
            return hops == 0 ? 0f : sum / hops;
        }

        @Override
        public float bargeInThreshold() {
            return bargeInThreshold;
        }

        @Override
        public float listeningThreshold() {
            return listeningThreshold;
        }

        @Override
        public String engine() {
            return "ten";
        }

        /** Linear interpolation to 16 kHz; a 16 kHz window is only converted. */
        private void upsample(float[] window) {
            int length = window.length * factor;
            if (upsampled.length != length) {
                upsampled = new short[length];
            }
            for (int i = 0; i < window.length; i++) {
                float sample = window[i];
                for (int step = 0; step < factor; step++) {
                    float t = (step + 1f) / factor;
                    upsampled[i * factor + step] = toShort(previous + (sample - previous) * t);
                }
                previous = sample;
            }
        }

        private static short toShort(float sample) {
            return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sample * 32768f)));
        }

        @Override
        public synchronized void close() {
            if (handle != null) {
                library.ten_vad_destroy(new PointerByReference(handle));
                handle = null;
            }
        }
    }
}
