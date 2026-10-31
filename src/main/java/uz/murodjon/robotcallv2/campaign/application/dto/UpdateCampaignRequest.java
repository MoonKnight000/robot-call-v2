package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollment;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignNumbers;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSequence;

/**
 * Full edit of a campaign ({@code PUT /api/campaigns/{id}}) — the same groups as
 * {@link CreateCampaignRequest}, minus {@code type}, which is fixed at creation.
 *
 * <p>{@code version} is the one {@link CampaignRow} carried when the form was opened; a
 * campaign saved by someone else since is answered 409 instead of being overwritten.
 * Optional, so an older client that does not send it keeps working.
 *
 * <p>{@code dialing.mode} is fixed at creation like {@code type} and is ignored here;
 * {@code aiAgentId} and {@code dialing.callQueueId} are checked against it as on create.
 */
public record UpdateCampaignRequest(
        @NotBlank String name,
        Long aiAgentId,
        @Min(Campaign.MIN_PRIORITY) @Max(Campaign.MAX_PRIORITY) Integer priority,
        @Valid CampaignScheduleDto schedule,
        @Valid CampaignRetryDto retry,
        @Valid CampaignLimitsDto limits,
        @Valid CampaignRecurrenceDto recurrence,
        @Valid CampaignDialingDto dialing,
        CampaignNumbers numbers,
        CampaignSequence sequence,
        AutoEnrollment autoEnroll,
        Long version) {

    public int priorityOrDefault() {
        return priority != null ? priority : Campaign.DEFAULT_PRIORITY;
    }
}
