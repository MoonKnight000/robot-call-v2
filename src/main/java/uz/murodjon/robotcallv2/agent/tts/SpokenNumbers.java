package uz.murodjon.robotcallv2.agent.tts;

import java.time.LocalTime;

/**
 * Numbers written out the way a voice prompt says them, for text this application builds
 * itself — a queue position, the hour the office opens. The number-word classes stay
 * package-private behind this: the LLM's own text still goes through
 * {@link SpeechTextNormalizer}, which decides for itself when a digit run becomes speech.
 *
 * <p>Language is a BCP-47 tag; anything that is not Russian or English is read in Uzbek,
 * the platform's default.
 */
public final class SpokenNumbers {

    private SpokenNumbers() {
    }

    /** {@code 129} → "bir yuz yigirma to'qqiz" / "сто двадцать девять". */
    public static String cardinal(long n, String language) {
        if (isRussian(language)) {
            return RussianNumberWords.cardinal(n);
        }
        if (isEnglish(language)) {
            return EnglishNumberWords.cardinal(n);
        }
        return UzbekNumberWords.cardinal(n);
    }

    /** {@code 129} → "bir yuz yigirma to'qqizinchi" / "сто двадцать девятый". */
    public static String ordinal(long n, String language) {
        if (isRussian(language)) {
            return RussianNumberWords.ordinal(n);
        }
        if (isEnglish(language)) {
            return n + englishSuffix(n);
        }
        return UzbekNumberWords.ordinal(n);
    }

    /**
     * A clock time as a prompt reads it: 9:00 → "to'qqiz" / "девять ноль-ноль",
     * 14:30 → "o'n to'rt o'ttiz" / "четырнадцать тридцать". The caller adds the case
     * ending ("soat to'qqiz<b>da</b>", "<b>в</b> девять ноль-ноль").
     */
    public static String clockTime(LocalTime time, String language) {
        int hour = time.getHour();
        int minute = time.getMinute();
        if (isEnglish(language)) {
            return String.format("%d:%02d", hour, minute);
        }
        String hours = cardinal(hour, language);
        if (minute == 0) {
            return isRussian(language) ? hours + " ноль-ноль" : hours;
        }
        String minutes = cardinal(minute, language);
        if (minute < 10) {
            minutes = cardinal(0, language) + " " + minutes;
        }
        return hours + " " + minutes;
    }

    static boolean isRussian(String language) {
        return language != null && language.toLowerCase().startsWith("ru");
    }

    static boolean isEnglish(String language) {
        return language != null && language.toLowerCase().startsWith("en");
    }

    private static String englishSuffix(long n) {
        long lastTwo = n % 100;
        if (lastTwo >= 11 && lastTwo <= 13) {
            return "th";
        }
        return switch ((int) (n % 10)) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }
}
