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

    private final VadProperties props;
    private OrtEnvironment env;
    private volatile OrtSession session;

    public SileroVad(VadProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        String path = props.modelPath();
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
            session = env.createSession(path, new OrtSession.SessionOptions());
            log.info("Silero VAD ready (model={}, sampleRate={}, window={})",
                    path, props.sampleRate(), props.windowSamples());
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
     * Run one window through the model.
     *
     * @param window  {@code windowSamples} float samples in [-1, 1]
     * @param state   recurrent state in/out (from {@link #newState()} or a prior result)
     * @return speech probability, or {@code -1} on inference error
     */
    public float run(float[] window, float[][][] state) {
        OrtSession current = session;
        if (current == null) {
            return -1f;
        }
        Map<String, OnnxTensor> inputs = new HashMap<>();
        try (OnnxTensor in = OnnxTensor.createTensor(env, new float[][]{window});
             OnnxTensor st = OnnxTensor.createTensor(env, state);
             OnnxTensor sr = OnnxTensor.createTensor(env,
                     LongBuffer.wrap(new long[]{props.sampleRate()}), new long[]{})) {
            inputs.put("input", in);
            inputs.put("state", st);
            inputs.put("sr", sr);
            try (OrtSession.Result result = current.run(inputs)) {
                float prob = ((float[][]) result.get(0).getValue())[0][0];
                float[][][] next = (float[][][]) result.get(1).getValue();
                // Copy the returned state back into the caller's buffer.
                for (int a = 0; a < 2; a++) {
                    System.arraycopy(next[a][0], 0, state[a][0], 0, state[a][0].length);
                }
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
    }
}
