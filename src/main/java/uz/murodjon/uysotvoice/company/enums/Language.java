package uz.murodjon.uysotvoice.company.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * BCP-47 languages the platform can speak (report #6) — a closed set instead of the
 * previous free-text {@code String}, so the panel can offer a select instead of a text
 * field. {@link #code} is what crosses the API/DB boundary ({@code "uz-UZ"}, the same
 * format every STT/TTS provider's own {@code languageCode} already uses) — the enum
 * constant itself ({@code UZ_UZ}) is Java-only, never serialized directly.
 */
public enum Language {
    UZ_UZ("uz-UZ"),
    RU_RU("ru-RU"),
    EN_US("en-US");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** @throws IllegalArgumentException if {@code code} matches none of the supported languages */
    @JsonCreator
    public static Language fromCode(String code) {
        for (Language language : values()) {
            if (language.code.equalsIgnoreCase(code)) {
                return language;
            }
        }
        throw new IllegalArgumentException("Unknown language '" + code + "'; supported: uz-UZ, ru-RU, en-US");
    }
}
