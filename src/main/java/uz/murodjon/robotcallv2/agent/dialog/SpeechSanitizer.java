package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;

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
     * A tool name, anywhere in the line: a lowerCamelCase word with an internal capital
     * ({@code transitionTo}, {@code recordRefusalReason}). ASCII-only on purpose, so
     * Cyrillic is untouched, and it cannot start mid-word, so a company name written
     * {@code UzAuto} is not one.
     */
    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("\\b[a-z]+[A-Z][a-zA-Z]*\\b");

    /** ...and an FSM stage name: {@code DEBT_NOTICE}, {@code PAYMENT_NEGOTIATION}. */
    private static final Pattern STAGE_NAME_PATTERN = Pattern.compile("\\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\\b");

    /**
     * Anything a spoken Uzbek or Russian line cannot be made of. What is kept: Latin and
     * Cyrillic letters, digits, whitespace, the apostrophes Uzbek writes o' and g' with
     * (ASCII, U+02BB, U+02BC, U+2019), sentence punctuation, and the brackets, braces,
     * angle brackets and underscores the checks above identify code tokens and thought
     * tags by — stripping those would turn {@code <thought>} into a word to read aloud.
     */
    private static final Pattern UNSPEAKABLE_CHAR_PATTERN = Pattern.compile(
            "[^\\p{IsLatin}\\p{IsCyrillic}0-9\\s.,!?:;…\\-—–'ʻʼ’\"«»„“”()\\[\\]{}<>_/%№+]");

    /**
     * Gemini's bare thinking marker: the English word "thought" glued to the end of a
     * sentence or arriving as a chunk of its own. A recorded call ended with
     * "…kelishdikmi, Murodjon aka?thought" and the caller heard the English word read out.
     * Only at a chunk start or straight after sentence punctuation, so a word that merely
     * contains it ("thoughtful") is untouched — and spoken Uzbek or Russian never has it.
     */
    private static final Pattern THOUGHT_MARKER_PATTERN = Pattern.compile(
            "(?:^|(?<=[.!?…]))\\s*thought\\b:?", Pattern.CASE_INSENSITIVE);

    /**
     * A stage tag the model put in front of its line, copying the bracketed
     * {@code [SYSTEM: …]} notes it is sent: a recorded call had "[SEARCH] Navoiy turar joy
     * majmuasidan …" synthesized and stored. Upper-case Latin only and only at a chunk start
     * or after sentence punctuation, so a bracket in ordinary speech is left alone.
     */
    private static final Pattern STAGE_TAG_PATTERN = Pattern.compile(
            "(?:^\\s*|(?<=[.!?…]\\s{0,3}))\\[[A-Z][A-Z0-9_]*]\\s*");

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
        if (lower.contains("dynamic_thought") || lower.contains("thought_or_fallback") || lower.contains("thoughtsignature")
                || lower.contains("[ism]") || lower.contains("[name]")) {
            return true;
        }

        // A tool or stage name anywhere in an otherwise ordinary sentence. Every check
        // above is a whole-string verdict, and a leak never arrives that way: on a
        // recorded call the caller heard "Qachon to'lay olasiz?ibu kabi bosqichda
        // transitionTo yoki recordRefusalReason kabilarni o'ylash kerak. Hozircha
        // DEBT_NOTICE dan PAYMENT_NEGOTIATION ga o'tishimiz kerak." — the model narrating
        // its own next move. Neither spelling exists in spoken Uzbek or Russian, so
        // finding one is enough to drop the sentence.
        if (TOOL_NAME_PATTERN.matcher(trimmed).find() || STAGE_NAME_PATTERN.matcher(trimmed).find()) {
            return true;
        }

        // One word written in two alphabets is never speech: the fast backup model greeted a simulated
        // caller with "…qulaymi? рооg'iroq qilyapman." — the tail of another sentence, half Cyrillic.
        return hasMixedScriptWord(trimmed);
    }

    /** Whether a word (split on spaces and hyphens) holds both Latin and Cyrillic letters. */
    static boolean hasMixedScriptWord(String text) {
        for (String word : text.split("[\\s\\-—–]+")) {
            boolean latin = false;
            boolean cyrillic = false;
            for (int i = 0; i < word.length(); i++) {
                Character.UnicodeScript script = Character.UnicodeScript.of(word.charAt(i));
                latin |= script == Character.UnicodeScript.LATIN;
                cyrillic |= script == Character.UnicodeScript.CYRILLIC;
            }
            if (latin && cyrillic) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code reply} is written in the other alphabet from what the caller just said — Latin
     * (Uzbek) to a caller speaking Cyrillic (Russian), or the other way round. Only when both sides are
     * clearly one script: a short "ha"/"да" or a line that mixes a brand name in decides nothing. The
     * fast backup model answered a Russian-speaking simulated caller in Uzbek, twice in a row.
     *
     * <p>Uzbek written in Cyrillic counts as the other language too: it carries letters Russian never
     * has (ў қ ғ ҳ), and "Хайр, саломат бўлинг!" was said to a Russian caller (eval #20) because both
     * sides were Cyrillic.
     *
     * @param clientLine what the caller last said, or null when the call has not heard them yet
     */
    static boolean isOtherScript(String reply, String clientLine) {
        Character.UnicodeScript heard = findDominantScript(clientLine, MIN_SCRIPT_LETTERS_HEARD, 4, 5);
        // Two thirds for the reply: "Нигина aka, yaxshimisiz?" — Uzbek with the client's name in
        // Cyrillic, 70% Latin — went past an 80% bar to a Russian-speaking simulated caller.
        Character.UnicodeScript said = findDominantScript(reply, MIN_SCRIPT_LETTERS_SAID, 2, 3);
        if (heard == null || said == null) {
            return false;
        }
        return heard != said || heard == Character.UnicodeScript.CYRILLIC
                && hasUzbekCyrillic(reply) && !hasUzbekCyrillic(clientLine);
    }

    /** Whether the text has a letter only Uzbek Cyrillic uses. */
    private static boolean hasUzbekCyrillic(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (UZBEK_CYRILLIC_LETTERS.indexOf(text.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static final String UZBEK_CYRILLIC_LETTERS = "ўЎқҚғҒҳҲ";

    private static final int MIN_SCRIPT_LETTERS_HEARD = 6;
    private static final int MIN_SCRIPT_LETTERS_SAID = 8;

    /**
     * Latin or Cyrillic when at least {@code num/den} of the text's (at least {@code minLetters}) letters
     * are in it.
     */
    private static Character.UnicodeScript findDominantScript(String text, int minLetters, int num, int den) {
        if (text == null) {
            return null;
        }
        int latin = 0;
        int cyrillic = 0;
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.LATIN) {
                latin++;
            } else if (script == Character.UnicodeScript.CYRILLIC) {
                cyrillic++;
            }
        }
        int letters = latin + cyrillic;
        if (letters < minLetters) {
            return null;
        }
        if (latin * den >= letters * num) {
            return Character.UnicodeScript.LATIN;
        }
        return cyrillic * den >= letters * num ? Character.UnicodeScript.CYRILLIC : null;
    }

    /**
     * Whether {@code text} names one of the scenario's stages as written ({@code GREETING}). A
     * one-word stage id has no underscore, so {@link #isUnspeakable} lets it through: a simulated
     * call greeted with "…qulaymi? Lekin qoidalarga ko'ra, GREETING bosqichida faqat shuni aytish
     * kerak." — the model reading its own instructions out. Case-sensitive and whole-word, so the
     * ordinary word "greeting" or a company name in capitals that is not a stage id is untouched.
     */
    public static boolean namesStage(String text, ScenarioDefinition scenario) {
        if (text == null || scenario == null || scenario.stages() == null) {
            return false;
        }
        for (StageDef stage : scenario.stages()) {
            String id = stage.id();
            if (id != null && id.length() > 2 && Pattern.compile("\\b" + Pattern.quote(id) + "\\b").matcher(text).find()) {
                return true;
            }
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
     * the stored transcript. Gemini's bare "thought" marker goes the same way. Applied where model text enters ({@code LlmTurnClient.extractText},
     * {@code DialogTools.recordReply}), so the transcript and the audio see the same line.
     */
    public static String stripUnspeakableCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String speakable = UNSPEAKABLE_CHAR_PATTERN.matcher(text).replaceAll("");
        String withoutThought = THOUGHT_MARKER_PATTERN.matcher(speakable).replaceAll("");
        return STAGE_TAG_PATTERN.matcher(withoutThought).replaceAll("");
    }
}
