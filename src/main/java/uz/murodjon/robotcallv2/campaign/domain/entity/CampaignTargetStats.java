package uz.murodjon.robotcallv2.campaign.domain.entity;

/**
 * @param successfulTargets targets with at least one call that reached what the campaign was
 *                          after (a conversion); the "successful" of the list's successful / total
 */
public record CampaignTargetStats(
        long totalTargets,
        long calledTargets,
        long pendingTargets,
        long completedTargets,
        long successfulTargets
) {
    public static final CampaignTargetStats ZERO = new CampaignTargetStats(0, 0, 0, 0, 0);

    public CampaignTargetStats(long totalTargets, long calledTargets, long pendingTargets, long completedTargets) {
        this(totalTargets, calledTargets, pendingTargets, completedTargets, 0);
    }
}
