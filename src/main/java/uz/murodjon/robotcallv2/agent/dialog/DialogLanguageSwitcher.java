package uz.murodjon.robotcallv2.agent.dialog;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Detects in-call language change requests (Uzbek, Russian, English) and switches the session's active language.
 */
public final class DialogLanguageSwitcher {

    private static final Pattern RU_REQUEST = Pattern.compile(
            "\\b(po[ -]?russki|na[ -]?russkom|po[ -]?ruski|ruscha|rus tilida|ruscha gapiring|govorite po russki|na russkiy|перейдите на русский|по[ -]?русски|по[ -]?русски говорите|на русском)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern UZ_REQUEST = Pattern.compile(
            "\\b(o'?zbekcha|uzbekcha|o'?zbek tilida|uzbek tilida|po[ -]?uzbekski|o'?zbekcha gapiring|gapiring o'?zbekcha|по[ -]?узбекски|на узбекском)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern EN_REQUEST = Pattern.compile(
            "\\b(in english|english please|speak english|inglizcha|ingliz tilida)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private DialogLanguageSwitcher() {
    }

    /**
     * Checks user utterance for an explicit request to switch language.
     * @return New language code (e.g. "ru-RU", "uz-UZ", "en-US") or empty if no change requested.
     */
    public static Optional<String> detectLanguageSwitch(String userUtterance, String currentLanguage) {
        if (userUtterance == null || userUtterance.isBlank()) {
            return Optional.empty();
        }

        String cur = currentLanguage != null ? currentLanguage.toLowerCase(Locale.ROOT) : "uz-uz";

        if (RU_REQUEST.matcher(userUtterance).find()) {
            if (!cur.startsWith("ru")) {
                return Optional.of("ru-RU");
            }
        } else if (UZ_REQUEST.matcher(userUtterance).find()) {
            if (!cur.startsWith("uz")) {
                return Optional.of("uz-UZ");
            }
        } else if (EN_REQUEST.matcher(userUtterance).find()) {
            if (!cur.startsWith("en")) {
                return Optional.of("en-US");
            }
        }

        return Optional.empty();
    }
}
