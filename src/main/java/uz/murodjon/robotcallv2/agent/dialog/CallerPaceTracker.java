package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.stt.SpeechRate;

import java.util.Locale;

/**
 * Turns the caller's speaking rate into the bot's pace (human-like call plan, phase 4): a fast
 * caller gets a slightly faster voice and, past {@code hurriedRatio}, one-sentence replies; a slow
 * one a slightly slower voice. Bounded by {@code maxAdjustPercent} either way, so the voice never
 * stops sounding like itself. Read by {@link VoiceEmotionResolver} and {@link SystemPromptFactory}.
 */
@Component
public class CallerPaceTracker {

    private static final Logger log = LoggerFactory.getLogger(CallerPaceTracker.class);

    /** The pace is averaged over this many utterances, and only once two have been heard. */
    private static final int WINDOW = 3;
    private static final int MIN_READINGS = 2;

    private final CallerPaceProperties callerPaceProperties;

    public CallerPaceTracker(CallerPaceProperties callerPaceProperties) {
        this.callerPaceProperties = callerPaceProperties;
    }

    /**
     * One caller final and the speech time it took.
     *
     * @param s null when the call has no dialog session (nothing to adapt)
     */
    public void record(DialogSession s, String text, int speechMs) {
        if (s == null || callerPaceProperties == null || !callerPaceProperties.enabled()
                || callerPaceProperties.referenceWordsPerSecond() <= 0) {
            return;
        }
        double rate = SpeechRate.wordsPerSecond(text, speechMs);
        if (Double.isNaN(rate)) {
            return;
        }
        double pace = s.addCallerPace(rate, WINDOW, MIN_READINGS);
        if (Double.isNaN(pace)) {
            return;
        }
        double ratio = pace / callerPaceProperties.referenceWordsPerSecond();
        double factor = factorFor(ratio, callerPaceProperties.maxAdjustPercent());
        boolean hurried = callerPaceProperties.hurriedRatio() > 0 && ratio >= callerPaceProperties.hurriedRatio();
        if (hurried != s.isCallerHurried() || Math.abs(factor - s.callerPaceFactor()) >= 0.01) {
            log.info("[{}] caller pace {} words/s (x{} of reference) → voice speed x{}{}", s.channelId(),
                    format(pace), format(ratio), format(factor), hurried ? ", short replies" : "");
        }
        s.setCallerPace(factor, hurried);
    }

    /** The caller's pace relative to the reference, clamped to ±{@code maxAdjustPercent}. */
    static double factorFor(double ratio, int maxAdjustPercent) {
        double max = Math.max(0, maxAdjustPercent) / 100d;
        return Math.round(Math.clamp(ratio, 1 - max, 1 + max) * 100.0) / 100.0;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
