package uz.murodjon.robotcallv2.aiagent.domain.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Custom pronunciation or phonetic substitution rule for an AI Agent.
 *
 * <p>Rewrites acronyms, domain jargon, brand names or numbers into spoken phonetic
 * equivalents before passing text to the TTS engine (e.g. {@code "MCHJ" -> "mas'uliyati cheklangan jamiyat"}).
 *
 * @param word original word or acronym to match
 * @param replacement spoken phonetic replacement text
 * @param caseSensitive whether string matching respects case (default false)
 * @param language target language code or {@code null} / {@code "*"} for all languages
 */
public record PronunciationRule(
        @NotBlank @Size(max = 120) String word,
        @NotBlank @Size(max = 255) String replacement,
        boolean caseSensitive,
        String language
) {
}
