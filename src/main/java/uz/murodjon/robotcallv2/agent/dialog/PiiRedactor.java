package uz.murodjon.robotcallv2.agent.dialog;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts Personally Identifiable Information (PII) such as bank cards,
 * passport numbers, PINFLs, and CVV codes from transcripts and logs (PCI-DSS & GDPR compliance).
 */
public final class PiiRedactor {

    // 16-digit card numbers with optional spaces or hyphens (8600 xxxx xxxx xxxx, 9860..., 4xxx..., 5xxx..., etc.)
    private static final Pattern CARD_PATTERN = Pattern.compile(
            "\\b(?<prefix>\\d{4})[\\s\\-]?(?:\\d{4})[\\s\\-]?(?:\\d{4})[\\s\\-]?(?<suffix>\\d{4})\\b"
    );

    // 9-character Uzbekistan Passport in Latin or Cyrillic (e.g. AA 1234567, AB7654321, АА 1234567)
    private static final Pattern PASSPORT_PATTERN = Pattern.compile(
            "\\b(?<series>[A-Za-zА-Яа-яЁё]{2})[\\s\\-]?(?:\\d{4})(?<suffix>\\d{3})\\b"
    );

    // 14-digit PINFL (JSHSHIR), optional spaces or hyphens
    private static final Pattern PINFL_PATTERN = Pattern.compile(
            "\\b(?<prefix>\\d{4})(?:[\\s\\-]?\\d{6})[\\s\\-]?(?<suffix>\\d{4})\\b"
    );

    // 3-digit or 4-digit CVV / CVC with optional Uzbek labels (e.g. cvv 123, cvv kodi 123, xavfsizlik kodi 123)
    private static final Pattern CVV_PATTERN = Pattern.compile(
            "(?i)\\b(?:cvv(?:\\s*kodini|\\s*kodi)?|cvc(?:\\s*kodini|\\s*kodi)?|(?:xavfsizlik|maxfiy|karta)\\s*kodini?)[\\s:]+(\\d{3,4})\\b"
    );

    private PiiRedactor() {
    }

    /**
     * Redacts sensitive PII from the given text.
     */
    public static String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String result = text;

        // Redact cards -> 8600 **** **** 1234
        Matcher cardMatcher = CARD_PATTERN.matcher(result);
        if (cardMatcher.find()) {
            result = cardMatcher.replaceAll("${prefix} **** **** ${suffix}");
        }

        // Redact passports -> AA ****123
        Matcher passportMatcher = PASSPORT_PATTERN.matcher(result);
        if (passportMatcher.find()) {
            result = passportMatcher.replaceAll("${series} ****${suffix}");
        }

        // Redact PINFL -> 1234 ****** 5678
        Matcher pinflMatcher = PINFL_PATTERN.matcher(result);
        if (pinflMatcher.find()) {
            result = pinflMatcher.replaceAll("${prefix} ****** ${suffix}");
        }

        // Redact CVV -> CVV ***
        Matcher cvvMatcher = CVV_PATTERN.matcher(result);
        if (cvvMatcher.find()) {
            result = cvvMatcher.replaceAll("CVV ***");
        }

        return result;
    }
}
