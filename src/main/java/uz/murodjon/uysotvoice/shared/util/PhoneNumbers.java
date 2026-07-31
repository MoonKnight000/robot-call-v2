package uz.murodjon.uysotvoice.shared.util;

import uz.murodjon.uysotvoice.shared.exception.ValidationException;

import java.util.regex.Pattern;

/**
 * Validation and normalization for dialled numbers.
 *
 * <p>A number ends up concatenated into an Asterisk dial string
 * ({@code PJSIP/<number>@<endpoint>}), so an unchecked value is not merely invalid —
 * an embedded {@code @} silently redirects the call to another endpoint, and other
 * separators can alter the technology. Numbers reach us from the campaign API and
 * from {@code campaign_target.phone}, neither of which is trusted input, so every
 * dial path validates here.
 *
 * <p>Short numbers (3–4 digits) stay legal on purpose: they are internal extensions
 * used for softphone testing (see {@code voice-agent.asterisk.local-number-pattern}).
 */
public final class PhoneNumbers {

    /** E.164 allows at most 15 digits; 3 is the shortest internal extension we dial. */
    private static final Pattern VALID = Pattern.compile("^\\+?\\d{3,15}$");

    /** Formatting characters humans type that carry no meaning for dialling. */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s()\\-.]");

    private PhoneNumbers() {
    }

    /**
     * Strip formatting characters, keeping a leading {@code +}. Does not validate —
     * pass the result to {@link #isValid} or {@link #require}.
     *
     * @return the cleaned number, or {@code null} if {@code raw} was null
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return SEPARATORS.matcher(raw.trim()).replaceAll("");
    }

    /** Whether {@code number} is dialable as-is (already normalized). */
    public static boolean isValid(String number) {
        return number != null && VALID.matcher(number).matches();
    }

    /**
     * Normalize and validate in one step.
     *
     * @return the normalized number, ready to concatenate into a dial string
     * @throws IllegalArgumentException if it is not a dialable number (mapped to
     *         HTTP 400 by {@code ApiExceptionHandler})
     */
    public static String require(String raw) {
        String normalized = normalize(raw);
        if (!isValid(normalized)) {
            throw new ValidationException("Invalid phone number: '" + raw
                    + "' (expected 3-15 digits, optional leading '+')");
        }
        return normalized;
    }
}
