package uz.murodjon.robotcallv2.company.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * BCP-47 languages the platform can speak (report #6).
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

    /** @throws IllegalArgumentException if code matches none of the supported languages */
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
