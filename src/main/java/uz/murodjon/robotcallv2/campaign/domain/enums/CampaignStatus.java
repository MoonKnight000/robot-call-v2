package uz.murodjon.robotcallv2.campaign.domain.enums;

/** Lifecycle of a {@link Campaign} (PROJECT.md §6). */
public enum CampaignStatus {
    DRAFT,
    /** Started with a future {@code startAt}; the sweep makes it ACTIVE when that time comes. */
    SCHEDULED,
    ACTIVE,
    PAUSED,
    /** Every target reached a terminal state while the campaign was ACTIVE — see {@code CampaignOutcomeService#checkCompletion}. */
    COMPLETED,
    ARCHIVED
}
