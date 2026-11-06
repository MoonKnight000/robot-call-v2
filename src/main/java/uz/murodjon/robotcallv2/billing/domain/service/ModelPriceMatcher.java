package uz.murodjon.robotcallv2.billing.domain.service;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Finds the price-list row a provider-reported model id belongs to.
 *
 * <p>Providers answer with more specific names than the ones configured: OpenAI reports
 * {@code gpt-4o-mini-2024-07-18} for {@code gpt-4o-mini}, Gemini may prefix {@code models/}
 * or name a dated preview. Such a name maps to the row it extends — but only when what
 * follows is a version, never another model: {@code gpt-4o-mini} must not be priced as
 * {@code gpt-4o}.
 */
public final class ModelPriceMatcher {

    /** Model ids as stored: lower-case letters, digits and . _ : / - only. */
    private static final Pattern MODEL_ID = Pattern.compile("[a-z0-9][a-z0-9._:/-]{0,127}");
    /** What may follow a known id and still be the same model: a date/version, or a release tag. */
    private static final Pattern VERSION_SUFFIX = Pattern.compile("[-@:](\\d|latest|preview|exp).*");

    private ModelPriceMatcher() {
    }

    /** The id as stored: trimmed, lower-case, without Gemini's {@code models/} prefix; null when blank. */
    public static String normalize(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        String id = model.trim().toLowerCase(Locale.ROOT);
        return id.startsWith("models/") ? id.substring("models/".length()) : id;
    }

    /** Whether {@code model} (already normalized) is a well-formed id to store. */
    public static boolean isValidId(String model) {
        return model != null && MODEL_ID.matcher(model).matches();
    }

    /**
     * The known id {@code reported} is priced as: itself, or the longest known id it extends
     * by a version suffix; null when none fits.
     */
    public static String findMatch(String reported, Collection<String> knownIds) {
        String id = normalize(reported);
        if (id == null) {
            return null;
        }
        if (knownIds.contains(id)) {
            return id;
        }
        String best = null;
        for (String known : knownIds) {
            if (id.startsWith(known) && VERSION_SUFFIX.matcher(id.substring(known.length())).matches()
                    && (best == null || known.length() > best.length())) {
                best = known;
            }
        }
        return best;
    }
}
