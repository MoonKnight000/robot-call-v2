package uz.murodjon.robotcallv2.campaign.domain.enums;

/** Dial-loop state of a {@link CampaignTarget} (PROJECT.md §6). */
public enum TargetStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    FAILED,
    EXHAUSTED
}
