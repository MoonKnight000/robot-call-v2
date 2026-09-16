package uz.murodjon.robotcallv2.campaign.application.dto;

import java.util.List;

/**
 * @param skippedDuplicates numbers the campaign already had queued, left as they were
 * @param skippedDoNotCall  numbers on the company's do-not-call list, stored marked and never dialled
 */
public record AddTargetsResponse(long campaignId, int added, List<Long> targetIds, int skippedDuplicates,
                                 int skippedDoNotCall) {
}
