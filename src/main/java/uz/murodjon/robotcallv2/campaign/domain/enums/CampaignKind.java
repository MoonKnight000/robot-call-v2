package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * How a campaign gets its contacts. Fixed when the campaign is created.
 */
public enum CampaignKind {
    /** The call list is built by hand: contacts picked, a CSV uploaded, or a target source synced. */
    STANDARD,
    /** Contacts are added automatically as they match the campaign's rules. */
    AUTO_ENROLL
}
