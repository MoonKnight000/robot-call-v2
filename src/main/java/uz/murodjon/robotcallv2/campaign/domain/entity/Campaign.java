package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * A calling job: who is called, when, how often and how many times.
 *
 * <p>Everything about <em>how the call sounds</em> — the scenario, the voice, the persona,
 * the model, the ambient sound, the trunks — belongs to the {@code AiAgent} this campaign
 * names (V12). A campaign used to carry both, which meant running the same script with a
 * Russian voice required cloning the whole campaign, and an inbound call, having no
 * campaign at all, could not be given those settings by any means.
 *
 * @param aiAgentId the agent whose voice and script this campaign's calls run under
 */
public record Campaign(
        long id,
        String name,
        CampaignType type,
        CampaignStatus status,
        LocalTime dialWindowStart,
        LocalTime dialWindowEnd,
        Set<DayOfWeek> dialDays,
        int maxAttempts,
        int retryIntervalMinutes,
        int maxConcurrentCalls,
        int dailyCallCap,
        long aiAgentId,
        long companyId,
        Long createdBy,
        RecurrenceType recurrenceType,
        Integer recurringDayOfMonth,
        String cronExpression,
        boolean autoResetTargets,
        Instant lastRunAt
) {

    public Set<DayOfWeek> allowedDays() {
        return dialDays == null || dialDays.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : dialDays;
    }
}
