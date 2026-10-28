package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;

/**
 * A new calling job ({@code POST /api/campaigns}). Each group may be left out as a whole; its
 * fields then take their defaults.
 *
 * <p>Only who is called and when. The voice, the persona, the scenario, the model and the
 * trunks come from the {@code aiAgentId} — see {@code POST /api/ai-agents}.
 *
 * @param type      required (backend-uchun-talablar.md §9a) — previously an omitted/blank
 *                  value silently fell back to {@code DEBT_COLLECTION}, which surprised
 *                  callers that meant to leave it unset; a missing value is now a 400
 * @param aiAgentId the agent from {@code GET /api/ai-agents} whose voice and scenario this
 *                  campaign's calls run under — required when {@code dialing.mode} is AI
 *                  or PREDICTIVE (whose overflow it takes).
 *                  Changeable later: an agent is a way of speaking, not the campaign's identity
 */
public record CreateCampaignRequest(
        @NotBlank String name,
        @NotNull CampaignType type,
        Long aiAgentId,
        @Min(Campaign.MIN_PRIORITY) @Max(Campaign.MAX_PRIORITY) Integer priority,
        @Valid CampaignScheduleDto schedule,
        @Valid CampaignRetryDto retry,
        @Valid CampaignLimitsDto limits,
        @Valid CampaignRecurrenceDto recurrence,
        @Valid CampaignDialingDto dialing) {

    public int priorityOrDefault() {
        return priority != null ? priority : Campaign.DEFAULT_PRIORITY;
    }
}
