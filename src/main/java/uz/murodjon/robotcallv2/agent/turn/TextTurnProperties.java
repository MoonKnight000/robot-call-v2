package uz.murodjon.robotcallv2.agent.turn;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * End-of-turn detection from the <em>words</em> of the utterance (NAMO Turn Detector v1
 * Multilingual, an mmBERT classifier exported to ONNX). Bound from
 * {@code voice-agent.text-turn.*}.
 *
 * <p>The counterpart of {@link SmartTurnProperties}, which listens to the audio. It reads
 * the recognizer's latest interim instead, so it judges meaning — "to'lov qilaman, lekin"
 * is unfinished however the voice fell. Used only where Smart Turn is not installed for the
 * call, and it changes nothing but how long the speech gate waits, the same two ways.
 *
 * <p><b>uz-UZ is not among the 23 languages the published model was fine-tuned on</b>
 * (Turkish and Russian are; the mmBERT base it starts from did see Uzbek). That is why
 * {@link #languages()} defaults to ru-RU: add uz-UZ only once a replay run over real
 * recordings ({@code TurnReplayTool}) says the verdicts hold.
 *
 * @param enabled        master switch. Off, or with either file missing, nothing is loaded
 * @param modelPath      path to the ONNX graph ({@code model_quant.onnx})
 * @param tokenizerPath  path to the model's {@code tokenizer.json}
 * @param languages      BCP-47 languages the model is trusted for; any other call keeps its timer
 * @param threshold      p(end of turn) at or above which the utterance counts as finished.
 *                       Below it the wait is extended by {@code maxExtendMs}
 * @param maxExtendMs    extra silence an unfinished-reading utterance earns
 * @param earlyWaitMs    silence after which a confidently finished utterance closes early;
 *                       0 disables early closing
 * @param earlyThreshold p(end of turn) required for that — far above {@code threshold},
 *                       because cutting a caller off costs more than waiting
 * @param maxTokens      longest utterance scored, in tokens; a longer one is cut to its start
 */
@ConfigurationProperties(prefix = "voice-agent.text-turn")
public record TextTurnProperties(
        boolean enabled,
        String modelPath,
        String tokenizerPath,
        List<String> languages,
        float threshold,
        int maxExtendMs,
        int earlyWaitMs,
        float earlyThreshold,
        int maxTokens
) {

    /** Whether a call in {@code language} is one this model is trusted for. */
    public boolean supports(String language) {
        if (language == null || languages == null) {
            return false;
        }
        return languages.stream().anyMatch(language::equalsIgnoreCase);
    }
}
