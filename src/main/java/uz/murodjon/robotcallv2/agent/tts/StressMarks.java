package uz.murodjon.robotcallv2.agent.tts;

import java.util.regex.Pattern;

/**
 * Stress marks in text bound for TTS: a {@code +} right before the stressed vowel
 * ({@code "Sar+oy"}, {@code "з+амок"}) — SpeechKit's own TTS markup, written into an agent's
 * pronunciation rules. Yandex reads it; every other provider would say "plus", so for them
 * the marks are taken out ({@link TtsProvider#applyStressMarks}).
 */
final class StressMarks {

    /** A {@code +} that starts a letter run and does not follow a digit ("+998", "2+2" are left alone). */
    private static final Pattern MARK = Pattern.compile("(?<!\\p{N})\\+(?=\\p{L})");

    private StressMarks() {
    }

    /** {@code text} without its stress marks. */
    static String strip(String text) {
        if (text == null || text.indexOf('+') < 0) {
            return text;
        }
        return MARK.matcher(text).replaceAll("");
    }
}
