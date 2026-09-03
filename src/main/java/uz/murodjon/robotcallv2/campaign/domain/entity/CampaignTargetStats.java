package uz.murodjon.robotcallv2.campaign.domain.entity;

public record CampaignTargetStats(
        long totalTargets,
        long calledTargets,
        long pendingTargets,
        long completedTargets
) {
    public static final CampaignTargetStats ZERO = new CampaignTargetStats(0, 0, 0, 0);
}
