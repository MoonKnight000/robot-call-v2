package uz.murodjon.robotcallv2.agent.audio;

import ai.onnxruntime.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * The GTCRN speech-enhancement model, shared by every call (VOICE-PIPELINE-PLAN Bosqich 6).
 * Each call gets its own {@link NeuralDenoiseStream} holding the STFT buffers and the
 * model's recurrent caches; this class only owns the ONNX session, which is safe to run
 * from many threads at once.
 *
 * <p>Expects the streaming export: one STFT frame in ({@code [1, 257, 1, 2]} — 512-point
 * FFT at 16 kHz, real and imaginary), the enhanced frame out, plus any number of cache
 * tensors that go in as inputs and come back as outputs in the same order. The cache shapes
 * are read from the graph rather than written down here, so a re-export with a different
 * layout loads as long as it keeps that contract.
 *
 * <p>Non-fatal throughout, like the turn detectors: a missing file or a graph of another
 * shape logs why and leaves {@link #available()} false, and every call keeps the
 * broadband gate.
 */
@Component
@ConditionalOnProperty(prefix = "voice-agent.denoiser", name = "enabled", havingValue = "true")
public class NeuralDenoiser {

    private static final Logger log = LoggerFactory.getLogger(NeuralDenoiser.class);

    static final int N_FFT = 512;
    static final int HOP = 256;
    static final int BINS = N_FFT / 2 + 1;
    private static final long[] FRAME_SHAPE = {1, BINS, 1, 2};

    private final DenoiserProperties denoiserProperties;
    private final Timer hopLatency;

    private OrtEnvironment env;
    private volatile OrtSession session;
    private String frameInput;
    private final List<String> cacheInputs = new ArrayList<>();
    private final List<long[]> cacheShapes = new ArrayList<>();

    public NeuralDenoiser(DenoiserProperties denoiserProperties, MeterRegistry registry) {
        this.denoiserProperties = denoiserProperties;
        this.hopLatency = Timer.builder("voice.denoiser.hop.latency")
                .description("one 16 ms STFT hop through the neural denoiser — the CPU a call costs, 62.5 times a second")
                .publishPercentileHistogram()
                .register(registry);
    }

    @PostConstruct
    public void init() {
        String modelPath = denoiserProperties.modelPath();
        if (modelPath == null || modelPath.isBlank() || !Files.exists(Path.of(modelPath))) {
            log.error("Denoiser model {} not found (voice-agent.denoiser.model-path); calls keep the broadband gate",
                    modelPath);
            return;
        }
        try {
            env = OrtEnvironment.getEnvironment();
            OrtSession candidate;
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(1);
                options.setInterOpNumThreads(1);
                candidate = env.createSession(modelPath, options);
            }
            if (!readContract(candidate)) {
                candidate.close();
                return;
            }
            session = candidate;
            log.info("Neural denoiser ready (model={}, caches={})", modelPath, cacheInputs);
        } catch (Exception e) {
            log.error("Failed to load the denoiser model {}: {}", modelPath, e.getMessage());
            session = null;
        }
    }

    /** Checks the graph is a streaming export this class can drive, and learns its cache shapes. */
    private boolean readContract(OrtSession candidate) throws OrtException {
        List<Map.Entry<String, NodeInfo>> inputs = new ArrayList<>(candidate.getInputInfo().entrySet());
        List<NodeInfo> outputs = new ArrayList<>(candidate.getOutputInfo().values());
        if (inputs.isEmpty() || outputs.size() != inputs.size()) {
            log.error("Denoiser model has {} inputs and {} outputs; expected a frame plus caches in and out — disabled",
                    inputs.size(), outputs.size());
            return false;
        }
        for (int i = 0; i < inputs.size(); i++) {
            long[] in = shapeOf(inputs.get(i).getValue());
            long[] out = shapeOf(outputs.get(i));
            if (in == null || !Arrays.equals(in, out) || Arrays.stream(in).anyMatch(d -> d <= 0)) {
                log.error("Denoiser input {} has shape {} but output {} has {}; expected fixed, matching shapes — disabled",
                        inputs.get(i).getKey(), Arrays.toString(in), i, Arrays.toString(out));
                return false;
            }
            if (i == 0) {
                if (!Arrays.equals(in, FRAME_SHAPE)) {
                    log.error("Denoiser frame input {} is {}; expected {} (512-point STFT at 16 kHz) — disabled",
                            inputs.get(i).getKey(), Arrays.toString(in), Arrays.toString(FRAME_SHAPE));
                    return false;
                }
                frameInput = inputs.get(i).getKey();
            } else {
                cacheInputs.add(inputs.get(i).getKey());
                cacheShapes.add(in);
            }
        }
        return true;
    }

    private static long[] shapeOf(NodeInfo node) {
        return node.getInfo() instanceof TensorInfo tensor ? tensor.getShape() : null;
    }

    public boolean available() {
        return session != null;
    }

    /** A new call's denoiser state, or null when the model is not loaded. */
    public NeuralDenoiseStream newStream() {
        if (!available()) {
            return null;
        }
        float[][] caches = new float[cacheShapes.size()][];
        for (int i = 0; i < caches.length; i++) {
            caches[i] = new float[(int) Arrays.stream(cacheShapes.get(i)).reduce(1, (a, b) -> a * b)];
        }
        return new NeuralDenoiseStream(this, caches);
    }

    /**
     * One hop: the frame and the call's caches in, the enhanced frame out. The caches are
     * replaced in place with what the model returned.
     */
    float[] enhance(float[] frame, float[][] caches) throws OrtException {
        long started = System.nanoTime();
        Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
        try {
            inputs.put(frameInput, OnnxTensor.createTensor(env, FloatBuffer.wrap(frame), FRAME_SHAPE));
            for (int i = 0; i < caches.length; i++) {
                inputs.put(cacheInputs.get(i), OnnxTensor.createTensor(env, FloatBuffer.wrap(caches[i]), cacheShapes.get(i)));
            }
            try (OrtSession.Result result = session.run(inputs)) {
                float[] enhanced = read(result, 0, frame.length);
                for (int i = 0; i < caches.length; i++) {
                    caches[i] = read(result, i + 1, caches[i].length);
                }
                return enhanced;
            }
        } finally {
            inputs.values().forEach(OnnxTensor::close);
            hopLatency.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }

    private static float[] read(OrtSession.Result result, int index, int length) throws OrtException {
        if (!(result.get(index) instanceof OnnxTensor tensor)) {
            throw new OrtException("Denoiser output " + index + " is not a tensor");
        }
        FloatBuffer buffer = tensor.getFloatBuffer();
        if (buffer.remaining() != length) {
            throw new OrtException("Denoiser output " + index + " has " + buffer.remaining() + " values, expected " + length);
        }
        float[] values = new float[length];
        buffer.get(values);
        return values;
    }

    @PreDestroy
    public void shutdown() {
        OrtSession current = session;
        session = null;
        if (current != null) {
            try {
                current.close();
            } catch (OrtException e) {
                log.debug("Denoiser session close failed: {}", e.getMessage());
            }
        }
    }
}
