package uz.murodjon.uysotvoice.campaign.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;
import uz.murodjon.uysotvoice.campaign.enums.CampaignType;
import uz.murodjon.uysotvoice.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * {@link Campaign} enriched for the API response with names resolved from the ids it
 * carries (backend-uchun-talablar.md §16/§18) — a projection over {@code campaign},
 * {@code scenario} and {@code app_user}, so it takes the {@code <Noun>Row} suffix rather
 * than bare {@code Campaign}.
 *
 * @param scenarioName resolved from {@link Campaign#scenarioId()}; {@code null} only if
 *                     the scenario was since deleted (ids are otherwise validated at write time)
 * @param createdByName resolved from {@link Campaign#createdBy()}; {@code null} if the
 *                     campaign has no associated creator (see {@link Campaign#createdBy()})
 */
public record CampaignRow(
        long id,
        String name,
        CampaignType type,
        CampaignStatus status,
        String goalPrompt,
        String defaultLanguage,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowEnd,
        Set<DayOfWeek> dialDays,
        int maxAttempts,
        int retryIntervalMinutes,
        int maxConcurrentCalls,
        String ttsVoice,
        int dailyCallCap,
        long scenarioId,
        String scenarioName,
        long companyId,
        boolean disclosureEnabled,
        Long createdBy,
        String createdByName
) {

    public static CampaignRow of(Campaign c, String scenarioName, String createdByName) {
        return new CampaignRow(
                c.id(), c.name(), c.type(), c.status(), c.goalPrompt(), c.defaultLanguage(),
                c.dialWindowStart(), c.dialWindowEnd(), c.dialDays(), c.maxAttempts(),
                c.retryIntervalMinutes(), c.maxConcurrentCalls(), c.ttsVoice(), c.dailyCallCap(),
                c.scenarioId(), scenarioName, c.companyId(), c.disclosureEnabled(),
                c.createdBy(), createdByName);
    }
}
