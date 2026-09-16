package uz.murodjon.robotcallv2.agent.vad;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the Silero VAD ONNX model once and runs single-window inference
 * (PROJECT.md §7.2). The model itself is stateless across {@link #run} calls — the
 * recurrent LSTM state is passed in and out, so a shared session serves all calls
 * while each {@link VadStream} keeps its own state.
 *
 * <p>Non-fatal: if the model file is missing or fails to load, the app still runs and
 * barge-in is simply disabled. This targets the Silero v5 interface (inputs
 * {@code input}/{@code state}/{@code sr}, state shape {@code [2,1,128]}); adjust if
 * your model file uses the v4 {@code h}/{@code c} split.
 */
@Component
@ConditionalOnProperty(prefix = "voice-agent.vad", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SileroVad {

    private static final Logger log = LoggerFactory.getLogger(SileroVad.class);

    /** The recurrent state's shape — {@link #newState()}. */
    private static final long[] STATE_SHAPE = {2, 1, 128};
    private static final int STATE_SIZE = 2 * 128;

    private final VadProperties vadProperties;
    private OrtEnvironment env;
    private volatile OrtSession session;
    /** The {@code sr} input never changes, so it is one tensor shared by every call's every window. */
    private OnnxTensor sampleRateTensor;

    /**
     * Input buffers reused window after window instead of three arrays and a map every
     * 32 ms. Per thread, because the session is shared by every call at once while a thread
     * scores one window at a time — each call's audio is read by its own RTP worker.
     */
    private final ThreadLocal<VadScratch> scratch = ThreadLocal.withInitial(VadScratch::new);

    /**
     * One thread's inputs. Direct and in native byte order: that is what lets ORT read them
     * in place, where a heap array is copied into a fresh native buffer on every run.
     */
    private static final class VadScratch {
        private final FloatBuffer state = directFloats(STATE_SIZE);
        private final Map<String, OnnxTensor> inputs = new HashMap<>(4);
        private FloatBuffer input = directFloats(0);
        private long[] inputShape = {1, 0};

        /** The input buffer, cleared and sized for {@code length} samples — reallocated only if that changes. */
        private FloatBuffer inputFor(int length) {
            if (inputShape[1] != length) {
                input = directFloats(length);
                inputShape = new long[]{1, length};
            }
            input.clear();
            return input;
        }

        private static FloatBuffer directFloats(int count) {
            return ByteBuffer.allocateDirect(count * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        }
    }

    public SileroVad(VadProperties vadProperties) {
        this.vadProperties = vadProperties;
    }

    @PostConstruct
    public void init() {
        String path = vadProperties.modelPath();
        if (path == null || path.isBlank()) {
            log.warn("Silero VAD model path not set (voice-agent.vad.model-path); barge-in disabled");
            return;
        }
        if (!Files.exists(Path.of(path))) {
            log.error("Silero VAD model not found at {}; barge-in disabled", path);
            return;
        }
        try {
            env = OrtEnvironment.getEnvironment();
            sampleRateTensor = OnnxTensor.createTensor(env,
                    LongBuffer.wrap(new long[]{vadProperties.sampleRate()}), new long[]{});
            // One thread per inference: every call scores its own 32 ms windows at the same
            // time, and ORT's default per-session thread pools had all of them contending
            // for every core over a model this small.
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(1);
                options.setInterOpNumThreads(1);
                session = env.createSession(path, options);
            }
            log.info("Silero VAD ready (model={}, sampleRate={}, window={})",
                    path, vadProperties.sampleRate(), vadProperties.windowSamples());
        } catch (Exception e) {
            log.error("Failed to load Silero VAD model {}: {}", path, e.getMessage());
            session = null;
        }
    }

    public boolean available() {
        return session != null;
    }

    /** Fresh recurrent state for a new stream: shape {@code [2,1,128]}, all zeros. */
    public float[][][] newState() {
        return new float[2][1][128];
    }

    /**
     * Fresh input context for a new stream: the tail of the window before this one, which
     * v5 wants in front of every window it scores. Zeros for the first one — there is no
     * audio before the call started, which is exactly what the model is shown.
     */
    public float[] newContext() {
        return new float[vadProperties.sampleRate() == 16000 ? 64 : 32];
    }

    /**
     * Run one window through the model.
     *
     * <p>v5 does not score a window on its own: it scores the window with the previous
     * one's last {@code context.length} samples in front of it, so the first convolution
     * looks back at real audio instead of a step up from nothing. The graph declares that
     * input length dynamic, so a bare window is accepted — no exception, no warning — and
     * answers with a probability that thrashes. On a recorded call one shouted 3.5 s
     * sentence scored 0.94, 0.31, 0.10, 0.53, 0.20, 0.71, 0.02 across its windows; fed
     * with the context it is 0.97-0.99 throughout, and silence stays at 0.00 either way.
     * Neither the gate nor barge-in can survive that: both need consecutive windows over
     * a threshold, so the caller was never heard for the rest of the call.
     *
     * @param window  {@code windowSamples} float samples in [-1, 1]
     * @param state   recurrent state in/out (from {@link #newState()} or a prior result)
     * @param context the previous window's tail in/out (from {@link #newContext()})
     * @return speech probability, or {@code -1} on inference error
     */
    public float run(float[] window, float[][][] state, float[] context) {
        OrtSession current = session;
        if (current == null) {
            return -1f;
        }
        VadScratch buffers = scratch.get();
        FloatBuffer input = buffers.inputFor(context.length + window.length);
        input.put(context).put(window).flip();
        FloatBuffer stateIn = buffers.state;
        stateIn.clear();
        for (float[][] layer : state) {
            stateIn.put(layer[0]);
        }
        stateIn.flip();
        // A tensor is still new per run — ORT takes ownership of one per call — but over the
        // reused direct buffers it is a view, not a copy.
        try (OnnxTensor in = OnnxTensor.createTensor(env, input, buffers.inputShape);
             OnnxTensor st = OnnxTensor.createTensor(env, stateIn, STATE_SHAPE)) {
            Map<String, OnnxTensor> inputs = buffers.inputs;
            inputs.put("input", in);
            inputs.put("state", st);
            inputs.put("sr", sampleRateTensor);
            try (OrtSession.Result result = current.run(inputs)) {
                float prob = ((OnnxTensor) result.get(0)).getFloatBuffer().get(0);
                // Copy the returned state back into the caller's buffer, layer by layer.
                FloatBuffer next = ((OnnxTensor) result.get(1)).getFloatBuffer();
                for (float[][] layer : state) {
                    next.get(layer[0]);
                }
                System.arraycopy(window, window.length - context.length, context, 0, context.length);
                return prob;
            }
        } catch (OrtException e) {
            log.warn("Silero VAD inference failed: {}", e.getMessage());
            return -1f;
        }
    }

    @PreDestroy
    public void shutdown() {
        OrtSession current = session;
        if (current != null) {
            try {
                current.close();
            } catch (OrtException e) {
                log.debug("VAD session close failed: {}", e.getMessage());
            }
        }
        if (sampleRateTensor != null) {
            sampleRateTensor.close();
        }
    }
}
