package uz.murodjon.robotcallv2.aiagent.application.dto;

/**
 * A supported speech language in the agent language picker (Retell-style).
 *
 * @param code       BCP-47 language tag (e.g. {@code uz-UZ}, {@code ru-RU})
 * @param name       English name (e.g. {@code Uzbek}, {@code Russian})
 * @param nativeName Name in its own language (e.g. {@code O'zbekcha}, {@code Русский})
 * @param country    Country / Region (e.g. {@code O'zbekiston}, {@code Rossiya})
 * @param flag       Country flag emoji (e.g. 🇺🇿, 🇷🇺)
 */
public record SpeechLanguageRow(
        String code,
        String name,
        String nativeName,
        String country,
        String flag
) {
}
