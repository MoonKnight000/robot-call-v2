package uz.murodjon.robotcallv2.agent.turn;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
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
import java.util.Set;

/**
 * Scores whether the words of an utterance read as a finished thought (NAMO Turn Detector
 * v1 Multilingual). One shared ONNX session and tokenizer serve every call; the model is
 * stateless between utterances.
 *
 * <p>Non-fatal throughout, like {@link SmartTurnDetector}: a missing file, a graph with
 * inputs this build does not know, or an inference error leave detection unavailable and
 * the calls on their timer. It never returns a number it did not compute.
 */
@Component
@ConditionalOnProperty(prefix = "voice-agent.text-turn", name = "enabled", havingValue = "true")
public class TextTurnDetector {

    private static final Logger log = LoggerFactory.getLogger(TextTurnDetector.class);

    /** The inputs a BERT-style export can declare; anything else is a graph this build cannot feed. */
    private static final Set<String> KNOWN_INPUTS = Set.of("input_ids", "attention_mask", "token_type_ids");

    private final TextTurnProperties textTurnProperties;

    private OrtEnvironment env;
    private volatile OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    private Set<String> inputNames;

    public TextTurnDetector(TextTurnProperties textTurnProperties) {
        this.textTurnProperties = textTurnProperties;
    }

    @PostConstruct
    public void init() {
        String modelPath = textTurnProperties.modelPath();
        String tokenizerPath = textTurnProperties.tokenizerPath();
        if (modelPath == null || modelPath.isBlank() || tokenizerPath == null || tokenizerPath.isBlank()) {
            log.warn("Text turn model or tokenizer path not set (voice-agent.text-turn.*); detection disabled");
            return;
        }
        if (!Files.exists(Path.of(modelPath)) || !Files.exists(Path.of(tokenizerPath))) {
            log.error("Text turn model {} or tokenizer {} not found; detection disabled", modelPath, tokenizerPath);
            return;
        }
        try {
            tokenizer = HuggingFaceTokenizer.newInstance(Path.of(tokenizerPath), Map.of(
                    "maxLength", String.valueOf(Math.max(8, textTurnProperties.maxTokens())),
                    "truncation", "true",
                    "padding", "false"));
            env = OrtEnvironment.getEnvironment();
            OrtSession candidate;
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(1);
                options.setInterOpNumThreads(1);
                candidate = env.createSession(modelPath, options);
            }
            inputNames = candidate.getInputNames();
            if (!inputNames.contains("input_ids") || !KNOWN_INPUTS.containsAll(inputNames)) {
                log.error("Text turn model declares inputs {}; expected input_ids [+ attention_mask, "
                        + "token_type_ids] — detection disabled", inputNames);
                candidate.close();
                return;
            }
            session = candidate;
            log.info("Text turn detector ready (model={}, inputs={}, languages={}, threshold={})",
                    modelPath, inputNames, textTurnProperties.languages(), textTurnProperties.threshold());
        } catch (Exception e) {
            log.error("Failed to load the text turn model {}: {}", modelPath, e.getMessage());
            session = null;
        }
    }

    public boolean available() {
        return session != null;
    }

    /**
     * Whether {@code transcript} reads as finished. Errs towards finished — no model, no
     * text or a failed inference answer {@code true}, leaving the wait as the timer set it.
     */
    public boolean isComplete(String transcript) {
        float probability = completion(transcript);
        return probability < 0 || probability >= textTurnProperties.threshold();
    }

    /**
     * Whether {@code transcript} is finished beyond the doubt it takes to close the turn
     * early. Errs the other way: anything unknown answers {@code false}, so the caller keeps
     * the wait they would have had.
     */
    public boolean isConfidentlyComplete(String transcript) {
        float probability = completion(transcript);
        return probability >= 0 && probability >= textTurnProperties.earlyThreshold();
    }

    /** p(end of turn) for {@code transcript}, or {@code -1} when it could not be scored. */
    private float completion(String transcript) {
        OrtSession current = session;
        if (current == null || transcript == null || transcript.isBlank()) {
            return -1f;
        }
        Encoding encoding = tokenizer.encode(transcript.trim());
        long[] ids = encoding.getIds();
        long[] shape = {1, ids.length};
        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape));
            if (inputNames.contains("attention_mask")) {
                inputs.put("attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(encoding.getAttentionMask()), shape));
            }
            if (inputNames.contains("token_type_ids")) {
                inputs.put("token_type_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(new long[ids.length]), shape));
            }
            try (OrtSession.Result result = current.run(inputs)) {
                float probability = endOfTurn(result.get(0).getValue());
                log.debug("Text turn: p(end)={}", probability);
                return probability;
            }
        } catch (Exception e) {
            log.warn("Text turn inference failed: {}", e.getMessage());
            return -1f;
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    /** Softmax of the two logits {@code [not end, end]}, as the model card defines them. */
    private static float endOfTurn(Object value) throws OrtException {
        if (!(value instanceof float[][] logits) || logits.length == 0 || logits[0].length < 2) {
            throw new OrtException("Text turn output is not [1, 2] logits");
        }
        double notEnd = logits[0][0];
        double end = logits[0][1];
        double max = Math.max(notEnd, end);
        double e1 = Math.exp(end - max);
        return (float) (e1 / (e1 + Math.exp(notEnd - max)));
    }

    @PreDestroy
    public void shutdown() {
        OrtSession current = session;
        if (current != null) {
            try {
                current.close();
            } catch (OrtException e) {
                log.debug("Text turn session close failed: {}", e.getMessage());
            }
        }
        if (tokenizer != null) {
            tokenizer.close();
        }
    }
}
