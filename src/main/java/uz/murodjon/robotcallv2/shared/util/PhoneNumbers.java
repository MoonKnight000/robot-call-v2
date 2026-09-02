package uz.murodjon.robotcallv2.shared.util;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.regex.Pattern;

/**
 * Validation and normalization for dialed and stored telephone numbers.
 *
 * <p>Ensures all phone numbers across DB, CRM, campaign targets, contacts, and Asterisk dial strings
 * are parsed and normalized consistently to standard E.164 (e.g. {@code +998901234567}).
 *
 * <p>Short internal extensions (3–4 digits) stay legal without alteration on purpose:
 * they are internal extensions used for softphone testing
 * (see {@code voice-agent.asterisk.local-number-pattern}).
 */
public final class PhoneNumbers {

    public static final String DEFAULT_REGION = "UZ";
    public static final String E164_REGEX = "^\\+?[1-9]\\d{8,14}$";

    /** Internal extensions (3–4 digits) used for test softphones. */
    private static final Pattern INTERNAL_EXTENSION = Pattern.compile("^\\d{3,4}$");

    /** Characters that are never part of a phone number and indicate injection or garbage. */
    private static final Pattern FORBIDDEN_CHARS = Pattern.compile("[@&#/?!a-zA-Z]");

    /** Standard Uzbek 9-digit local subscriber number. */
    private static final Pattern UZ_LOCAL_9_DIGITS = Pattern.compile("^[389]\\d{8}$");

    private PhoneNumbers() {
    }

    /**
     * Normalizes a raw phone number input into canonical E.164 format (e.g. {@code +998901234567}).
     * Short internal extensions (e.g. {@code 600}, {@code 6001}) are preserved as-is.
     *
     * @param raw the raw input string
     * @return the normalized phone number, or null if {@code raw} is null or blank
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (INTERNAL_EXTENSION.matcher(trimmed).matches()) {
            return trimmed;
        }
        if (FORBIDDEN_CHARS.matcher(trimmed).find()) {
            return null;
        }

        // Clean spaces, hyphens, brackets
        String digitsOnly = trimmed.replaceAll("[^0-9+]", "");
        if (digitsOnly.isEmpty()) {
            return null;
        }

        if (digitsOnly.startsWith("+")) {
            String digits = digitsOnly.substring(1);
            if (digits.length() >= 9 && digits.length() <= 15) {
                return "+" + digits;
            }
            return null;
        }

        // Local UZ 9 digits (e.g. 901234567 -> +998901234567)
        if (UZ_LOCAL_9_DIGITS.matcher(digitsOnly).matches()) {
            return "+998" + digitsOnly;
        }

        // UZ with 8 prefix (e.g. 8901234567 -> +998901234567)
        if (digitsOnly.length() == 10 && digitsOnly.startsWith("8")) {
            String sub = digitsOnly.substring(1);
            if (UZ_LOCAL_9_DIGITS.matcher(sub).matches()) {
                return "+998" + sub;
            }
        }

        // UZ full 12 digits (998901234567 -> +998901234567)
        if (digitsOnly.length() == 12 && digitsOnly.startsWith("998")) {
            return "+" + digitsOnly;
        }

        // International without plus
        if (digitsOnly.length() >= 10 && digitsOnly.length() <= 15) {
            return "+" + digitsOnly;
        }

        return null;
    }

    /**
     * Checks if the given raw phone number is valid.
     */
    public static boolean isValid(String raw) {
        return normalize(raw) != null;
    }

    /**
     * Normalizes and validates the phone number in one step.
     *
     * @param raw raw phone number string
     * @return canonical E.164 format string or extension digits
     * @throws ValidationException if the number is invalid or cannot be normalized
     */
    public static String require(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) {
            throw new ValidationException(ErrorCode.PHONE_INVALID, raw);
        }
        return normalized;
    }

    /**
     * Formats a phone number for display in international format (e.g. {@code +998 90 123 45 67}).
     */
    public static String formatInternational(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (INTERNAL_EXTENSION.matcher(trimmed).matches()) {
            return trimmed;
        }
        String normalized = normalize(trimmed);
        if (normalized == null) {
            return trimmed;
        }
        if (normalized.startsWith("+998") && normalized.length() == 13) {
            return String.format("+998 %s %s %s %s",
                    normalized.substring(4, 6),
                    normalized.substring(6, 9),
                    normalized.substring(9, 11),
                    normalized.substring(11, 13));
        }
        return normalized;
    }

    /**
     * Formats a phone number for display in national format (e.g. {@code (90) 123 45 67}).
     */
    public static String formatNational(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (INTERNAL_EXTENSION.matcher(trimmed).matches()) {
            return trimmed;
        }
        String normalized = normalize(trimmed);
        if (normalized == null) {
            return trimmed;
        }
        if (normalized.startsWith("+998") && normalized.length() == 13) {
            return String.format("(%s) %s-%s-%s",
                    normalized.substring(4, 6),
                    normalized.substring(6, 9),
                    normalized.substring(9, 11),
                    normalized.substring(11, 13));
        }
        return normalized;
    }
}
