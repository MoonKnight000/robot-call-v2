package uz.murodjon.robotcallv2.campaign.domain.entity;

/**
 * Which numbers a campaign's calls present as caller ID.
 *
 * @param callerIdPoolId the caller ID pool the numbers come from; null falls back to the trunk's
 *                       own caller ID
 * @param localPresence  pick the pool number that shares the longest prefix with the number being
 *                       called (same region or operator), so the call shows up as a local one;
 *                       needs {@code callerIdPoolId}
 * @param fromNumberId   one of the company's phone numbers every call presents instead of a pool —
 *                       the call goes out on that number's own trunk. Not together with
 *                       {@code callerIdPoolId}; with neither, calls go out over the agent's trunks
 */
public record CampaignNumbers(Long callerIdPoolId, boolean localPresence, Long fromNumberId) {
}
