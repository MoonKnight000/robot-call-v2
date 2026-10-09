package uz.murodjon.robotcallv2.campaign.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryPolicy;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryRule;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetOrdering;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

/**
 * Full edit of a campaign ({@code PUT /api/campaigns/{id}}) — the same shape as
 * {@link CreateCampaignRequest}, minus {@code type}, which is fixed at creation.
 *
 * <p>{@code version} is the one {@link CampaignRow} carried when the form was opened; a
 * campaign saved by someone else since is answered 409 instead of being overwritten.
 * Optional, so an older client that does not send it keeps working.
 *
 * <p>{@code dialMode} is fixed at creation like {@code type}; {@code aiAgentId} and
 * {@code callQueueId} are checked against it as on create.
 */
public record UpdateCampaignRequest(
        @NotBlank String name,
        Long aiAgentId,
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
        Boolean autoResetTargets,
        @Min(Campaign.MIN_PRIORITY) @Max(Campaign.MAX_PRIORITY) Integer priority,
        Map<Disposition, RetryRule> retryPolicy,
        Instant startAt,
        Instant endAt,
        @Min(1) Long budgetUzs,
        @Min(1) Long dailyBudgetUzs,
        Long version,
        Long callQueueId,
        @Min(0) @Max(100) Integer maxAiOverflowPercent,
        TargetOrdering targetOrdering) {

    public RecurrenceType recurrenceTypeOrDefault() {
        return recurrenceType != null ? recurrenceType : RecurrenceType.ONCE;
    }

    public boolean autoResetTargetsOrDefault() {
        return autoResetTargets != null && autoResetTargets;
    }

    public int priorityOrDefault() {
        return priority != null ? priority : Campaign.DEFAULT_PRIORITY;
    }

    public RetryPolicy retryPolicyOrNone() {
        return retryPolicy == null ? RetryPolicy.NONE : new RetryPolicy(retryPolicy);
    }
}
