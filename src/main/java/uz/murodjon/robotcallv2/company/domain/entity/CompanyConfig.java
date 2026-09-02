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
}
