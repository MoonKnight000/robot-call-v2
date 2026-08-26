package uz.murodjon.uysotvoice.agent.turn;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Semantic end-of-turn detection: whether the caller has finished a thought, not merely
 * stopped making noise. Bound from {@code voice-agent.turn.*}.
 *
 * <p>The VAD can only say that the line went quiet, and the wait that follows is a guess
 * about what that silence means — the same guess whether the caller said "ha" or stopped
 * halfway through a contract number. Smart Turn v3 listens to the utterance itself and
 * scores how likely it is to be complete, which is the one signal a timer cannot carry.
 *
 * <p>It is used only to <em>extend</em> the wait, never to shorten it: an utterance the
 * model calls unfinished gets {@link #maxExtendMs()} more silence before the turn is
 * closed. Cutting early on its say-so would put a model with no Uzbek in its training set
 * in charge of when a caller is interrupted, and it is not paid for by anything.
 *
 * <p><b>Which is also why {@link #languages()} exists.</b> The published model covers 23
 * languages and uz-UZ is not among them; ru-RU is. A call in a language not listed here
 * never reaches the model and keeps the timer it has today
 * ({@code DynamicEndpointingProperties}).
 *
 * @param enabled     master switch. Off, or with no model file, the wait is the timer's
 *                    alone and nothing below is loaded
 * @param modelPath   path to the Smart Turn ONNX file. Blank leaves detection unavailable,
 *                    exactly as a missing VAD model does — non-fatal, and logged
 * @param languages   BCP-47 languages the model is trusted for. A call in anything else
 *                    is left to the timer
 * @param threshold   completion probability at or above which the utterance is taken as
 *                    finished. Lower waits more often
 * @param maxExtendMs how much extra silence an utterance scored unfinished earns. Bounded
 *                    because the model is sometimes wrong and the caller is waiting
 * @param nFft        STFT size for the log-mel features the graph expects. Must be a power
 *                    of two
 * @param hopSamples  STFT hop. With {@code nFft} and {@code frames} it has to reproduce the
 *                    model's declared input shape, which is checked at startup
 * @param mels        mel bands, i.e. the middle dimension of {@code input_features}
 * @param frames      spectrogram frames, i.e. the last dimension. {@code frames * hop}
 *                    samples of 16 kHz audio are scored — 8 seconds at the defaults
 */
@ConfigurationProperties(prefix = "voice-agent.turn")
public record SmartTurnProperties(
        boolean enabled,
        String modelPath,
        List<String> languages,
        float threshold,
        int maxExtendMs,
        int nFft,
        int hopSamples,
        int mels,
        int frames
) {

    /** Whether a call in {@code language} is one this model was trained for. */
    public boolean supports(String language) {
        if (language == null || languages == null) {
            return false;
        }
        return languages.stream().anyMatch(language::equalsIgnoreCase);
    }
}
