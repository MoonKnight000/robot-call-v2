package uz.murodjon.robotcallv2.campaign.application.dto;

import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;

import java.time.Instant;

/**
 * {@link Campaign} enriched for the API response with names resolved from the ids it
 * carries (backend-uchun-talablar.md §16/§18) — a projection over {@code campaign},
 * {@code ai_agent} and {@code app_user}, so it takes the {@code <Noun>Row} suffix rather
 * than bare {@code Campaign}. The groups are the ones {@link UpdateCampaignRequest} takes.
 */
public record CampaignRow(
        long id,
        String name,
        CampaignStatus status,
        Long aiAgentId,
        String aiAgentName,
        long companyId,
        Long createdBy,
        String createdByName,
        int priority,
        CampaignScheduleDto schedule,
        CampaignRetryDto retry,
        CampaignLimitsDto limits,
        CampaignRecurrenceDto recurrence,
        CampaignDialingDto dialing,
        CampaignTargetStats stats,
        Instant lastRunAt,
        long version
) {

    public static CampaignRow of(Campaign c, String aiAgentName, String createdByName) {
        return of(c, aiAgentName, createdByName, CampaignTargetStats.ZERO);
    }

    public static CampaignRow of(Campaign c, String aiAgentName, String createdByName, CampaignTargetStats stats) {
        return new CampaignRow(
                c.id(), c.name(), c.status(),
                c.aiAgentId(), aiAgentName, c.companyId(),
                c.createdBy(), createdByName,
                c.priority(),
                CampaignScheduleDto.of(c.schedule()),
                CampaignRetryDto.of(c.retry()),
                CampaignLimitsDto.of(c.limits()),
                CampaignRecurrenceDto.of(c.recurrence()),
                CampaignDialingDto.of(c.dialing()),
                stats != null ? stats : CampaignTargetStats.ZERO,
                c.lastRunAt(),
                c.version());
    }
}
