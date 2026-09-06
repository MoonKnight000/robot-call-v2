package uz.murodjon.robotcallv2.company.domain.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/**
 * Domain record for company operational settings (ROADMAP B.1/B.3).
 */
public record CompanyConfig(
        long id,
        long companyId,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowEnd,
        String timezone,
        Language defaultLanguage,
        List<Language> supportedLanguages,
        String disclosureText,
        Instant createdAt
) {

    private static final LocalTime DEFAULT_DIAL_WINDOW_START = LocalTime.of(7, 0);
    private static final LocalTime DEFAULT_DIAL_WINDOW_END = LocalTime.of(23, 0);
    private static final String DEFAULT_TIMEZONE = "Asia/Tashkent";
    private static final Language DEFAULT_LANGUAGE = Language.UZ_UZ;
    private static final List<Language> DEFAULT_SUPPORTED_LANGUAGES =
            List.of(Language.UZ_UZ, Language.RU_RU);
    private static final String DEFAULT_DISCLOSURE_TEXT =
            "Assalomu alaykum! Bu {company} kompaniyasining avtomatik ovozli xizmati. Suhbat yozib olinmoqda.";

    /**
     * What a tenant starts with before anyone edits its settings — the single definition,
     * used both when a company is created and when the default tenant is seeded at startup.
     */
    public static CompanyConfig defaults() {
        return settings(DEFAULT_DIAL_WINDOW_START, DEFAULT_DIAL_WINDOW_END, DEFAULT_TIMEZONE,
                DEFAULT_LANGUAGE, DEFAULT_SUPPORTED_LANGUAGES, DEFAULT_DISCLOSURE_TEXT);
    }

    /**
     * The settings alone, for a write. Identity and {@code createdAt} belong to the
     * storage layer, which stamps them itself.
     */
    public static CompanyConfig settings(LocalTime dialWindowStart, LocalTime dialWindowEnd, String timezone,
                                         Language defaultLanguage, List<Language> supportedLanguages,
                                         String disclosureText) {
        return new CompanyConfig(0, 0, dialWindowStart, dialWindowEnd, timezone,
                defaultLanguage, supportedLanguages, disclosureText, null);
    }
}
