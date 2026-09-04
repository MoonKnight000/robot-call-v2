package uz.murodjon.robotcallv2.agent.dialog;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates and sanitizes agent speech before synthesis or transcript logging.
 * Prevents raw LLM code tokens, template placeholders, or thinking artifacts
 * (e.g. "dynamic_thought_or_fallback", "<thought>", "{{...}}") from ever reaching the caller.
 */
public final class SpeechSanitizer {

    private static final Pattern SNAKE_CASE_PATTERN = Pattern.compile("^[a-z0-9]+(?:_[a-z0-9]+)+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUNCTION_CALL_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+\\s*\\(.*\\)$", Pattern.DOTALL);
    private static final Pattern XML_TAG_PATTERN = Pattern.compile("</?[a-zA-Z0-9_\\-]+(?:\\s+[^>]*)?>");

    private static final Set<String> FORBIDDEN_TOKENS = Set.of(
            "dynamic_thought_or_fallback",
            "dynamic_thought",
            "thought_or_fallback",
            "thought_signature",
            "thoughtsignature",
            "fallback",
            "null",
            "undefined",
            "none",
            "true",
            "false",
            "transitionto",
            "function_call",
            "tool_call"
    );

    private SpeechSanitizer() {
    }

    /**
     * Checks if the given text represents technical code, a template placeholder,
     * or an internal thought artifact that must NEVER be read aloud to the caller.
     */
    public static boolean isUnspeakable(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String trimmed = text.trim();

        // System note echoes
        if (SystemPromptFactory.isSystemNote(trimmed) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return true;
        }

        // Template placeholders
        if (trimmed.contains("{{") || trimmed.contains("}}")) {
            return true;
        }

        // Raw JSON or array
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return true;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);

        // Explicit forbidden tokens
        if (FORBIDDEN_TOKENS.contains(lower)) {
            return true;
        }

        // Snake_case identifiers (e.g. dynamic_thought_or_fallback)
        if (SNAKE_CASE_PATTERN.matcher(trimmed).matches()) {
            return true;
        }

        // Function call syntax e.g. transitionTo("REASON_INQUIRY")
        if (FUNCTION_CALL_PATTERN.matcher(trimmed).matches()) {
            return true;
        }

        // Words containing specific hallucinated phrases
        if (lower.contains("dynamic_thought") || lower.contains("thought_or_fallback") || lower.contains("thoughtsignature")) {
            return true;
        }

        return false;
    }

    /**
     * Strips XML/HTML tags (like <thought>...</thought>) and returns sanitized text,
     * or null if the entire text is unspeakable.
     */
    public static String sanitize(String text) {
        if (isUnspeakable(text)) {
            return null;
        }
        // Remove XML tags like <thought> or </thought> if present
        String stripped = XML_TAG_PATTERN.matcher(text).replaceAll("").trim();
        return isUnspeakable(stripped) ? null : stripped;
    }
}
