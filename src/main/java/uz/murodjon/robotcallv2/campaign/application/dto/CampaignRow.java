package uz.murodjon.robotcallv2.campaign.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

/**
 * {@link Campaign} enriched for the API response with names resolved from the ids it
 * carries (backend-uchun-talablar.md §16/§18) — a projection over {@code campaign},
 * {@code ai_agent} and {@code app_user}, so it takes the {@code <Noun>Row} suffix rather
 * than bare {@code Campaign}.
 */
public record CampaignRow(
        long id,
        String name,
        CampaignType type,
        CampaignStatus status,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowEnd,
        Set<DayOfWeek> dialDays,
        int maxAttempts,
        int retryIntervalMinutes,
        int maxConcurrentCalls,
        int dailyCallCap,
        long aiAgentId,
        String aiAgentName,
        long companyId,
        Long createdBy,
        String createdByName,
        RecurrenceType recurrenceType,
        Integer recurringDayOfMonth,
        String cronExpression,
        boolean autoResetTargets,
        Instant lastRunAt,
        long totalTargets,
        long calledTargets,
        long pendingTargets,
        long completedTargets
) {

    public static CampaignRow of(Campaign c, String aiAgentName, String createdByName) {
        return of(c, aiAgentName, createdByName, CampaignTargetStats.ZERO);
    }

    public static CampaignRow of(Campaign c, String aiAgentName, String createdByName, CampaignTargetStats stats) {
        CampaignTargetStats s = stats != null ? stats : CampaignTargetStats.ZERO;
        return new CampaignRow(
                c.id(), c.name(), c.type(), c.status(),
                c.dialWindowStart(), c.dialWindowEnd(), c.dialDays(), c.maxAttempts(),
                c.retryIntervalMinutes(), c.maxConcurrentCalls(), c.dailyCallCap(),
                c.aiAgentId(), aiAgentName, c.companyId(),
                c.createdBy(), createdByName,
                c.recurrenceType() != null ? c.recurrenceType() : RecurrenceType.ONCE,
                c.recurringDayOfMonth(), c.cronExpression(), c.autoResetTargets(), c.lastRunAt(),
                s.totalTargets(),
                s.calledTargets(),
                s.pendingTargets(),
                s.completedTargets());
    }
}
