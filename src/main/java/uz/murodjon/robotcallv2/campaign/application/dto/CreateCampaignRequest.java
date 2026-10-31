package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollment;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignNumbers;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSequence;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignKind;

/**
 * A new calling job ({@code POST /api/campaigns}). Each group may be left out as a whole; its
 * fields then take their defaults.
 *
 * <p>Only who is called and when. The voice, the persona, the scenario, the model and the
 * trunks come from the {@code aiAgentId} — see {@code POST /api/ai-agents}.
 *
 * @param aiAgentId the agent from {@code GET /api/ai-agents} whose voice and scenario this
 *                  campaign's calls run under — required when {@code dialing.mode} is AI
 *                  or PREDICTIVE (whose overflow it takes).
 */
public record CreateCampaignRequest(
        @NotBlank String name,
        Long aiAgentId,
        @Min(Campaign.MIN_PRIORITY) @Max(Campaign.MAX_PRIORITY) Integer priority,
        @Valid CampaignScheduleDto schedule,
        @Valid CampaignRetryDto retry,
        @Valid CampaignLimitsDto limits,
        @Valid CampaignRecurrenceDto recurrence,
        @Valid CampaignDialingDto dialing,
        CampaignNumbers numbers,
        CampaignKind kind,
        CampaignSequence sequence,
        AutoEnrollment autoEnroll) {

    public int priorityOrDefault() {
        return priority != null ? priority : Campaign.DEFAULT_PRIORITY;
    }
}
