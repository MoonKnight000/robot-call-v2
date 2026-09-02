package uz.murodjon.robotcallv2.campaign.domain.enums;

/** Lifecycle of a {@link Campaign} (PROJECT.md §6). */
public enum CampaignStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    /** Every target reached a terminal state while the campaign was ACTIVE — see {@code CampaignService#checkCompletion}. */
    COMPLETED,
    ARCHIVED
}
