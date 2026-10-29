package uz.murodjon.robotcallv2.campaign.application.dto;

import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;

/**
 * {@code POST /api/campaigns/batch-call} response.
 *
 * @param campaignId the campaign created for this batch
 * @param imported   the CSV import, row errors included
 * @param status     {@code ACTIVE}, or {@code SCHEDULED} for a future {@code schedule.startAt}
 */
public record BatchCallResponse(long campaignId, TargetImportResult imported, CampaignStatus status) {
}
