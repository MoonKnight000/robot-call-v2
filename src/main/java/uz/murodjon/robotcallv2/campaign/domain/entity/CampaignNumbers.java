package uz.murodjon.robotcallv2.campaign.domain.entity;

/**
 * Which numbers a campaign's calls present as caller ID.
 *
 * @param callerIdPoolId the caller ID pool the numbers come from; null falls back to the trunk's
 *                       own caller ID
 * @param localPresence  pick the pool number that shares the longest prefix with the number being
 *                       called (same region or operator), so the call shows up as a local one;
 *                       needs {@code callerIdPoolId}
 */
public record CampaignNumbers(Long callerIdPoolId, boolean localPresence) {
}
