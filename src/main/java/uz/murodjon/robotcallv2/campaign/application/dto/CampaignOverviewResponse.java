package uz.murodjon.robotcallv2.campaign.application.dto;

/**
 * The campaign page's Overview tab: the campaign, what its caller ID pool holds, and whether it
 * could be launched at this moment.
 *
 * @param callerIdPoolName   the pool the calls present numbers from; null when there is none
 * @param callerIdNumberCount numbers in that pool; 0 when there is none
 * @param launchBlocker      the first thing that stops a launch, or null when nothing does. The
 *                           legal-consent confirmation is not one — it is asked for at launch
 */
public record CampaignOverviewResponse(
        CampaignRow campaign,
        String callerIdPoolName,
        int callerIdNumberCount,
        LaunchBlocker launchBlocker
) {
}
