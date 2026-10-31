package uz.murodjon.robotcallv2.campaign.domain.entity;

/**
 * Retell's "Automatic retries" of a sequence campaign.
 *
 * @param doubleDial            an unanswered call gets one immediate second dial, as part of the same
 *                              attempt; never after a voicemail, a rejected number, or once the
 *                              campaign is no longer running
 * @param autoScheduleCallbacks a lead who asks to be called back is: at the time it asked for (set
 *                              by the agent's callback tool), else at the first time in the calling
 *                              window at least 24 hours later — and the step's attempts start over
 */
public record CampaignAutoRetries(boolean doubleDial, boolean autoScheduleCallbacks) {

    /** A campaign from before automatic retries, and any created without asking for them. */
    public static final CampaignAutoRetries NONE = new CampaignAutoRetries(false, false);
    /** Retell's default for a new campaign: both on. */
    public static final CampaignAutoRetries DEFAULT = new CampaignAutoRetries(true, true);
}
