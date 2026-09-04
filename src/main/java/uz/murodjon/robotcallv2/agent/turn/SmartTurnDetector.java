package uz.murodjon.robotcallv2.agent.turn;

import ai.onnxruntime.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Scores how likely it is that the caller has finished a thought, rather than merely
 * stopped making noise (Smart Turn v3, BSD-2). Loads the ONNX model once and serves every
 * call from the shared session, exactly as {@code SileroVad} does — the model is stateless
 * between utterances, so there is nothing per-call to keep.
 *
 * <p>Non-fatal throughout: a missing file, an unreadable graph, an input shape this build
 * cannot produce, or an inference error all leave detection unavailable and the calls
 * running on the timer they use today. The one thing it must never do is return a number
 * it did not really compute.
 *
 * <p><b>The startup shape check is the important part.</b> The features
 * ({@link WhisperFeatures}) are reproduced from a preprocessor config that is not in the
 * file, so a re-exported model with a different STFT would still take the tensor and still
 * return a probability — just not one that means anything. Rather than trust the settings,
 * the declared shape of {@code input_features} is compared against what this build would
 * produce, and a mismatch disables detection with both shapes in the log. A graph that
 * takes raw audio instead (a 2-D input) is served directly, no spectrogram involved.
 */
@Component
@ConditionalOnProperty(prefix = "voice-agent.turn", name = "enabled", havingValue = "true")
public class SmartTurnDetector {

    private static final Logger log = LoggerFactory.getLogger(SmartTurnDetector.class);

    /** The rate the model was trained at; the caller resamples to it. */
    static final int SAMPLE_RATE = 16000;

    private final SmartTurnProperties props;

    private OrtEnvironment env;
    private volatile OrtSession session;
    private String inputName;
    /** Null when the graph takes raw audio and no spectrogram is needed. */
    private WhisperFeatures features;
    private int audioSamples;

    public SmartTurnDetector(SmartTurnProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        String path = props.modelPath();
        if (path == null || path.isBlank()) {
            log.warn("Smart Turn model path not set (voice-agent.turn.model-path); "
                    + "semantic end-of-turn detection disabled");
            return;
        }
        if (!Files.exists(Path.of(path))) {
            log.error("Smart Turn model not found at {}; semantic end-of-turn detection disabled", path);
            return;
        }
        try {
            env = OrtEnvironment.getEnvironment();
            OrtSession candidate = env.createSession(path, new OrtSession.SessionOptions());
            if (!accept(candidate)) {
                candidate.close();
                return;
            }
            session = candidate;
            log.info("Smart Turn ready (model={}, input={}, {}, languages={}, threshold={})",
                    path, inputName, features == null ? audioSamples + " raw samples"
                            : features.nMels() + "x" + features.frames() + " log-mel",
                    props.languages(), props.threshold());
        } catch (Exception e) {
            log.error("Failed to load the Smart Turn model {}: {}", path, e.getMessage());
            session = null;
        }
    }

    /**
     * Work out how this graph wants its audio, and refuse it if this build cannot produce
     * that shape.
     *
     * @return whether the session is usable
     */
    private boolean accept(OrtSession candidate) throws OrtException {
        Map<String, NodeInfo> inputs = candidate.getInputInfo();
        if (inputs.size() != 1) {
            log.error("Smart Turn model declares {} inputs ({}); expected exactly one — detection disabled",
                    inputs.size(), inputs.keySet());
            return false;
        }
        NodeInfo node = inputs.values().iterator().next();
        inputName = node.getName();
        if (!(node.getInfo() instanceof TensorInfo tensor)) {
            log.error("Smart Turn input '{}' is not a tensor — detection disabled", inputName);
            return false;
        }
        long[] shape = tensor.getShape();
        if (shape.length == 2) {
            // Raw audio: the preprocessing is baked into the graph, so there is nothing
            // for WhisperFeatures to get wrong.
            audioSamples = (int) (shape[1] > 0 ? shape[1] : (long) props.frames() * props.hopSamples());
            features = null;
            return true;
        }
        if (shape.length != 3) {
            log.error("Smart Turn input '{}' has shape {} — expected (1, mels, frames) or (1, samples); "
                    + "detection disabled", inputName, java.util.Arrays.toString(shape));
            return false;
        }
        try {
            features = new WhisperFeatures(SAMPLE_RATE, props.nFft(), props.hopSamples(),
                    props.mels(), props.frames());
        } catch (IllegalArgumentException e) {
            log.error("Smart Turn features cannot be built: {} — detection disabled", e.getMessage());
            return false;
        }
        if (mismatch(shape[1], features.nMels()) || mismatch(shape[2], features.frames())) {
            log.error("Smart Turn model wants input_features {} but this build produces ({}, {}, {}). "
                            + "Set voice-agent.turn.{{mels,frames,n-fft,hop-samples}} to match the model's "
                            + "preprocessor config — detection disabled until they agree.",
                    java.util.Arrays.toString(shape), 1, features.nMels(), features.frames());
            features = null;
            return false;
        }
        audioSamples = features.samples();
        return true;
    }

    /** A dynamic axis (negative) declares nothing, so only a stated size can disagree. */
    private static boolean mismatch(long declared, int produced) {
        return declared > 0 && declared != produced;
    }

    public boolean available() {
        return session != null;
    }

    /** How much 16 kHz audio one score consumes. Shorter input is padded with silence. */
    public int samples() {
        return audioSamples;
    }

    /**
     * Whether the utterance ending at the end of {@code pcm} sounds finished.
     *
     * <p>Errs towards finished on purpose: an unavailable model, an inference failure or
     * an empty buffer all return {@code true}, which leaves the wait exactly as the timer
     * set it. The opposite default would let a broken model hold every caller on the line
     * for the full extension.
     *
     * @param pcm    16 kHz mono PCM, most recent samples last
     * @param length how much of {@code pcm} is populated
     * @see #isConfidentlyComplete(short[], int) for the question asked in the other direction
     */
    public boolean isComplete(short[] pcm, int length) {
        float probability = completion(pcm, length);
        return probability < 0 || probability >= props.threshold();
    }

    /**
     * Whether the utterance is finished beyond the doubt it takes to close the turn
     * <em>early</em>, before the timer's wait has run out.
     *
     * <p>The opposite default to {@link #isComplete}, and for the same reason. That one
     * decides whether to add silence, so an unknown answer is harmless; this one decides
     * whether to cut a caller off mid-breath, so an unknown answer must never say yes. A
     * model that is unavailable, failing, or merely unsure leaves the caller the wait they
     * would have had.
     */
    public boolean isConfidentlyComplete(short[] pcm, int length) {
        float probability = completion(pcm, length);
        return probability >= 0 && probability >= props.earlyThreshold();
    }

    /**
     * The model's probability that the utterance ending at the end of {@code pcm} is
     * finished, or {@code -1} when it could not be scored at all.
     *
     * @param pcm    16 kHz mono PCM, most recent samples last
     * @param length how much of {@code pcm} is populated
     */
    private float completion(short[] pcm, int length) {
        OrtSession current = session;
        if (current == null || pcm == null || length <= 0) {
            return -1f;
        }
        try (OnnxTensor input = tensor(pcm, length);
             OrtSession.Result result = current.run(Map.of(inputName, input))) {
            float probability = firstFloat(result.get(0).getValue());
            log.debug("Smart Turn: p(complete)={}", probability);
            return probability;
        } catch (Exception e) {
            log.warn("Smart Turn inference failed: {}", e.getMessage());
            return -1f;
        }
    }

    private OnnxTensor tensor(short[] pcm, int length) throws OrtException {
        if (features == null) {
            float[] audio = new float[audioSamples];
            int available = Math.min(length, audioSamples);
            int start = length - available;
            for (int i = 0; i < available; i++) {
                audio[i] = pcm[start + i] / 32768f;
            }
            return OnnxTensor.createTensor(env, FloatBuffer.wrap(audio), new long[]{1, audioSamples});
        }
        float[] mel = features.extract(pcm, length);
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(mel),
                new long[]{1, features.nMels(), features.frames()});
    }

    /**
     * The first number in the output, whatever rank the graph wrapped it in — exports of
     * this model have shipped it as {@code [1]} and as {@code [1,1]}.
     */
    private static float firstFloat(Object value) {
        Object current = value;
        while (current != null && current.getClass().isArray()) {
            if (current instanceof float[] floats) {
                return floats[0];
            }
            current = java.lang.reflect.Array.get(current, 0);
        }
        throw new IllegalStateException("Smart Turn output is not a float tensor");
    }

    @PreDestroy
    public void shutdown() {
        OrtSession current = session;
        if (current != null) {
            try {
                current.close();
            } catch (OrtException e) {
                log.debug("Smart Turn session close failed: {}", e.getMessage());
            }
        }
    }
}
