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

    /**
     * Anything a spoken Uzbek or Russian line cannot be made of. What is kept: Latin and
     * Cyrillic letters, digits, whitespace, the apostrophes Uzbek writes o' and g' with
     * (ASCII, U+02BB, U+02BC, U+2019), sentence punctuation, and the brackets, braces,
     * angle brackets and underscores the checks above identify code tokens and thought
     * tags by — stripping those would turn {@code <thought>} into a word to read aloud.
     */
    private static final Pattern UNSPEAKABLE_CHAR_PATTERN = Pattern.compile(
            "[^\\p{IsLatin}\\p{IsCyrillic}0-9\\s.,!?:;…\\-—–'ʻʼ’\"«»„“”()\\[\\]{}<>_/%№+]");

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

        // Thought tags. The whole reply goes, not just the tags: what sits between
        // <thought> and </thought> is the model reasoning with itself, so stripping the
        // markup would leave exactly the words the caller must not hear. Handled the way
        // a system-note echo is — dropped, and the turn's fallback line covers it.
        if (XML_TAG_PATTERN.matcher(trimmed).find()) {
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
     * Drops the individual characters that cannot be spoken, leaving the rest of the line
     * intact.
     *
     * <p>Every other check here is a whole-string verdict, so one stray character makes an
     * otherwise good sentence neither speakable nor droppable: a real call ended with
     * "Xayr, salomat bo'ling!읍", and the Hangul syllable reached both the synthesizer and
     * the stored transcript. Applied where model text enters ({@code TurnRunner.textOf},
     * {@code DialogTools.recordReply}), so the transcript and the audio see the same line.
     */
    public static String stripUnspeakableCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return UNSPEAKABLE_CHAR_PATTERN.matcher(text).replaceAll("");
    }
}
