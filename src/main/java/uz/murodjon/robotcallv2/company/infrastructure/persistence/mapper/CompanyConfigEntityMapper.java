package uz.murodjon.robotcallv2.company.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyConfigEntity;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CompanyConfigEntityMapper {

    public CompanyConfig toCompanyConfig(CompanyConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CompanyConfig(
                entity.getId(),
                entity.getCompanyId(),
                entity.getDialWindowStart(),
                entity.getDialWindowEnd(),
                entity.getTimezone(),
                entity.getDefaultLanguage(),
                List.copyOf(entity.getSupportedLanguages()),
                entity.getDisclosureText(),
                entity.getMaxAttemptsPerPhonePerWeek(),
                entity.getQuietDaysAfterConversation(),
                entity.isRespectPromisedDate(),
                entity.isHumanLikePersonaAllowed(),
                parseHolidays(entity.getHolidays()),
                entity.getScorecardReviewThreshold(),
                entity.getCreatedAt()
        );
    }

    /** The stored {@code holidays} column as dates; an entry that does not parse is skipped. */
    public static List<LocalDate> parseHolidays(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        List<LocalDate> days = new ArrayList<>();
        for (String part : stored.split(",")) {
            try {
                days.add(LocalDate.parse(part.trim()));
            } catch (DateTimeParseException ignored) {
                // written by this class only; a hand-edited row loses the bad date, not the rest
            }
        }
        return days;
    }

    /** Dates as the {@code holidays} column stores them: sorted, once each. */
    public static String formatHolidays(List<LocalDate> holidays) {
        return holidays.stream().distinct().sorted().map(LocalDate::toString).collect(Collectors.joining(","));
    }
}
