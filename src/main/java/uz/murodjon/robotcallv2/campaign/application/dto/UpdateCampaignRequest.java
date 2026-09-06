package uz.murodjon.robotcallv2.campaign.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * Full edit of a campaign ({@code PUT /api/campaigns/{id}}) — the same shape as
 * {@link CreateCampaignRequest}, minus {@code type}, which is fixed at creation.
 */
public record UpdateCampaignRequest(
        @NotBlank String name,
        @NotNull Long aiAgentId,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowEnd,
        Set<DayOfWeek> dialDays,
        int maxAttempts,
        int retryIntervalMinutes,
        int maxConcurrentCalls,
        int dailyCallCap,
        RecurrenceType recurrenceType,
        @Min(1) @Max(31) Integer recurringDayOfMonth,
        String cronExpression,
        Boolean autoResetTargets) {

    public RecurrenceType recurrenceTypeOrDefault() {
        return recurrenceType != null ? recurrenceType : RecurrenceType.ONCE;
    }

    public boolean autoResetTargetsOrDefault() {
        return autoResetTargets != null && autoResetTargets;
    }
}
