package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository;

public interface CampaignTargetSummaryProjection {
    Long getCampaignId();
    long getTotalTargets();
    long getCalledTargets();
    long getPendingTargets();
    long getCompletedTargets();
}
