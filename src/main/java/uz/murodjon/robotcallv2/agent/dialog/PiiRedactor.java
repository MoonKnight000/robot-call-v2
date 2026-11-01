package uz.murodjon.robotcallv2.agent.dialog;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts Personally Identifiable Information (PII) such as bank cards,
 * passport numbers, PINFLs, and CVV codes from transcripts and logs (PCI-DSS & GDPR compliance).
 */
public final class PiiRedactor {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"
    );

    // 16-digit card numbers with optional spaces or hyphens (8600 xxxx xxxx xxxx, 9860..., 4xxx..., 5xxx..., etc.)
    private static final Pattern CARD_PATTERN = Pattern.compile(
            "\\b(?<prefix>\\d{4})[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?(?<suffix>\\d{4})\\b"
    );

    // 9-character Uzbekistan Passport in Latin or Cyrillic (e.g. AA 1234567, AB7654321, АА 1234567)
    private static final Pattern PASSPORT_PATTERN = Pattern.compile(
            "\\b(?<series>[A-Za-zА-Яа-яЁё]{2})[\\s\\-]?\\d{4}(?<suffix>\\d{3})\\b"
    );

    // 14-digit PINFL (JSHSHIR), optional spaces or hyphens
    private static final Pattern PINFL_PATTERN = Pattern.compile(
            "\\b(?<prefix>\\d{4})[\\s\\-]?\\d{6}[\\s\\-]?(?<suffix>\\d{4})\\b"
    );

    // 3-digit or 4-digit CVV / CVC with optional Uzbek labels (e.g. cvv 123, cvv kodi 123, xavfsizlik kodi 123)
    private static final Pattern CVV_PATTERN = Pattern.compile(
            "(?i)\\b(?:cvv(?:\\s*kodini|\\s*kodi)?|cvc(?:\\s*kodini|\\s*kodi)?|(?:xavfsizlik|maxfiy|karta)\\s*kodini?)[\\s:]+(\\d{3,4})\\b"
    );

    // International numbers: optional +, 1-3 digit country code, then 2 + 3 + 2 + 2 digits (+998 90 123 45 67).
    private static final Pattern INTERNATIONAL_PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)\\+?\\d{1,3}[\\s\\-]?\\(?\\d{2}\\)?[\\s\\-]?\\d{3}[\\s\\-]?\\d{2}[\\s\\-]?\\d{2}(?!\\d)"
    );

    // Uzbek local mobile numbers: an operator code, then 3 + 2 + 2 digits (90 123 45 67). The code keeps a plain amount out.
    private static final Pattern LOCAL_PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:33|50|55|6[1-9]|7[0-9]|88|9[0-9])[\\s\\-]?\\d{3}[\\s\\-]?\\d{2}[\\s\\-]?\\d{2}(?!\\d)"
    );

    private PiiRedactor() {
    }

    /** As {@link #redact(String)}, plus phone numbers when {@code agent} asks for it ({@code redactPhoneNumbers}). */
    public static String redact(String text, uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent agent) {
        return redact(text, agent != null && agent.redactPhoneNumbers());
    }

    /**
     * Redacts sensitive PII from the given text.
     */
    public static String redact(String text) {
        return redact(text, false);
    }

    /**
     * As {@link #redact(String)}; with {@code phoneNumbers} a phone number is replaced by
     * {@code [PHONE]} too. That is opt-in: the number a caller leaves for a call back is often
     * exactly what the summary has to extract from the stored transcript.
     */
    public static String redact(String text, boolean phoneNumbers) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String result = text;

        result = EMAIL_PATTERN.matcher(result).replaceAll("[EMAIL]");

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

        if (phoneNumbers) {
            result = INTERNATIONAL_PHONE_PATTERN.matcher(result).replaceAll("[PHONE]");
            result = LOCAL_PHONE_PATTERN.matcher(result).replaceAll("[PHONE]");
        }

        return result;
    }
}
