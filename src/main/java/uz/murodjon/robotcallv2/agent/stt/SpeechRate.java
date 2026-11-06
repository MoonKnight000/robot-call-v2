package uz.murodjon.robotcallv2.agent.stt;

/**
 * A caller's speaking rate on one utterance: the final's words over the VAD-scored speech time
 * ({@code SpeechGate#lastUtteranceSpeechMs}), pauses left out.
 */
public final class SpeechRate {

    /** Below this much speech, or this few words, the rate says nothing. */
    static final int MIN_SPEECH_MS = 600;
    static final int MIN_WORDS = 2;

    private SpeechRate() {
    }

    /** @return words per second, or {@code NaN} when the utterance is too short to tell */
    public static double wordsPerSecond(String text, int speechMs) {
        int words = text == null || text.isBlank() ? 0 : text.strip().split("\\s+").length;
        if (speechMs < MIN_SPEECH_MS || words < MIN_WORDS) {
            return Double.NaN;
        }
        return words * 1000d / speechMs;
    }
}
