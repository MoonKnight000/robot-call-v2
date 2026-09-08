package uz.murodjon.robotcallv2.secret.domain.service;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code {{secrets.KEY}}} placeholder language, and the inverse operation of taking
 * the values back out of a string that is about to be logged or shown.
 *
 * <p>Both halves live here rather than in the service because the substitution has one
 * caller that is not the service — a tool call being built during a conversation — and
 * two implementations of the same pattern would drift.
 */
public final class SecretPlaceholders {

    /** Matches {{secrets.KEY}} and the singular {{secret.KEY}} people write by mistake. */
    private static final Pattern PATTERN = Pattern.compile("\\{\\{\\s*secrets?\\.([A-Za-z0-9_.-]+)\\s*}}");

    /** What a redacted value reads as. Long enough to notice, short enough not to dominate a log line. */
    private static final String REDACTED = "***";

    /** A secret shorter than this is not distinctive enough to search for in a log line. */
    private static final int MIN_REDACTABLE_LENGTH = 4;

    private SecretPlaceholders() {
    }

    /**
     * Substitutes every known placeholder in {@code text}.
     *
     * <p>An unknown key is left as it was written. Substituting an empty string here would
     * send a request that looks valid and is unauthenticated; the untouched placeholder is
     * what makes the misconfiguration visible in the tool's response.
     */
    public static String resolve(String text, Map<String, String> secrets) {
        if (text == null || !text.contains("{{")) {
            return text;
        }
        Matcher matcher = PATTERN.matcher(text);
        if (!matcher.find()) {
            return text;
        }

        matcher.reset();
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String value = secrets != null ? secrets.get(matcher.group(1)) : null;
            matcher.appendReplacement(resolved,
                    Matcher.quoteReplacement(value != null ? value : matcher.group(0)));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    /**
     * Replaces any of {@code values} appearing in {@code text} with {@code ***}.
     *
     * <p>For log lines and for anything handed back to the model: once a placeholder has
     * been resolved, the string carries the credential itself, and a URL with the key in
     * its query string is the ordinary case rather than the exotic one. Very short values
     * are skipped — a two-character secret would blank out unrelated text and hide more
     * than it protects.
     */
    public static String redact(String text, Collection<String> values) {
        if (text == null || text.isEmpty() || values == null || values.isEmpty()) {
            return text;
        }
        String redacted = text;
        for (String value : values) {
            if (value != null && value.length() >= MIN_REDACTABLE_LENGTH) {
                redacted = redacted.replace(value, REDACTED);
            }
        }
        return redacted;
    }
}
