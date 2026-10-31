package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * @param score        0–100, how likely the number is to pay ({@link TargetScore}); null until scored
 * @param bestCallHour the company-local hour the number most often talks in; null when unknown
 * @param stepId       the sequence step the target is on; null means the sequence's start step
 * @param stepAttempts calls spent on that step, counting one in progress
 * @param outcome      name of the sequence outcome its last answered call reached ("Other",
 *                     "No Response" included); null before any
 * @param converted    left the sequence through an End successful route
 */
public record CampaignTarget(
        long id,
        long campaignId,
        long clientId,
        String phone,
        String language,
        String contextData,
        TargetStatus status,
        int attempts,
        boolean doNotCall,
        Instant nextAttemptAt,
        Integer score,
        Integer bestCallHour,
        String stepId,
        int stepAttempts,
        String outcome,
        boolean converted
) {

    /** The pre-V38 shape: no outcome reached yet. */
    public CampaignTarget(long id, long campaignId, long clientId, String phone, String language,
                          String contextData, TargetStatus status, int attempts, boolean doNotCall,
                          Instant nextAttemptAt, Integer score, Integer bestCallHour, String stepId,
                          int stepAttempts) {
        this(id, campaignId, clientId, phone, language, contextData, status, attempts, doNotCall, nextAttemptAt,
                score, bestCallHour, stepId, stepAttempts, null, false);
    }

    /**
     * How long an operator holds a preview card. A claim sets {@code nextAttemptAt} to the
     * claim time plus this, so the claim time is {@code nextAttemptAt} minus it.
     */
    public static final Duration OPERATOR_CLAIM_LEASE = Duration.ofHours(2);

    /** The pre-V23 shape: not on a sequence step. */
    public CampaignTarget(long id, long campaignId, long clientId, String phone, String language,
                          String contextData, TargetStatus status, int attempts, boolean doNotCall,
                          Instant nextAttemptAt, Integer score, Integer bestCallHour) {
        this(id, campaignId, clientId, phone, language, contextData, status, attempts, doNotCall, nextAttemptAt,
                score, bestCallHour, null, 0);
    }

    /** The pre-V8 shape: not scored yet. */
    public CampaignTarget(long id, long campaignId, long clientId, String phone, String language,
                          String contextData, TargetStatus status, int attempts, boolean doNotCall,
                          Instant nextAttemptAt) {
        this(id, campaignId, clientId, phone, language, contextData, status, attempts, doNotCall, nextAttemptAt,
                null, null);
    }

    public CampaignTarget(long id, long campaignId, long clientId, String phone, String language,
                          String contextData, TargetStatus status, int attempts, boolean doNotCall) {
        this(id, campaignId, clientId, phone, language, contextData, status, attempts, doNotCall, null);
    }

    /**
     * A target queued for dialling. Identity is the storage layer's, and a new target
     * always starts PENDING with no attempts spent and no do-not-call mark.
     */
    public static CampaignTarget queued(long campaignId, long clientId, String phone, String language,
                                        String contextData) {
        return queued(campaignId, clientId, phone, language, contextData, false);
    }

    /**
     * The same, with the do-not-call mark decided at import: a number on the company's
     * list is still stored — the campaign's counts should show it — but never dialled.
     */
    public static CampaignTarget queued(long campaignId, long clientId, String phone, String language,
                                        String contextData, boolean doNotCall) {
        return new CampaignTarget(0, campaignId, clientId, phone, language, contextData,
                TargetStatus.PENDING, 0, doNotCall);
    }
}
