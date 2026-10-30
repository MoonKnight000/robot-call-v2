package uz.murodjon.robotcallv2.company.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import uz.murodjon.robotcallv2.company.domain.service.CompanyValidator;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * BCP-47 languages the platform can speak (report #6).
 */
public enum Language {
    UZ_UZ("uz-UZ"),
    RU_RU("ru-RU"),
    EN_US("en-US"),
    EN_GB("en-GB"),
    KK_KZ("kk-KZ"),
    TR_TR("tr-TR"),
    ES_ES("es-ES");

    /** What a call, chat or agent is held in when nothing names a language. */
    public static final Language DEFAULT = UZ_UZ;

    private final String code;

    Language(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** The language {@code code} names, case-insensitively; empty for anything else. */
    public static Optional<Language> findByCode(String code) {
        return Arrays.stream(values()).filter(language -> language.code.equalsIgnoreCase(code)).findFirst();
    }

    /**
     * Jackson's way in only; code reads a language with {@link CompanyValidator#parseLanguage}
     * or {@link #findByCode}.
     */
    @JsonCreator
    public static Language fromCode(String code) {
        return CompanyValidator.parseLanguage(code);
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(Language::code).toList();
    }
}
