package uz.murodjon.robotcallv2.campaign.application.port.input;

import com.fasterxml.jackson.databind.JsonNode;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.SpreadsheetCsv;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetScore;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallResponse;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.UploadedFile;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import uz.murodjon.robotcallv2.crm.domain.entity.Debtor;

import java.util.List;
import java.util.Map;

public interface CampaignTargetUseCase {

    long addTarget(long companyId, long campaignId, long clientId, String phone, String language,
                   JsonNode contextData);

    AddTargetsResponse addTargets(long companyId, long campaignId, List<AddTargetRequest> requests);

    TargetImportResult importTargetsCsv(long companyId, long campaignId, String csv);

    TargetCsvPreview previewTargetsCsv(long companyId, long campaignId, String csv);

    /** As {@link #importTargetsCsv}, with the person's column mapping (Retell "Map the columns"). */
    TargetImportResult importTargets(long companyId, long campaignId, TargetImportRequest request);

    /** As {@link #previewTargetsCsv}, with the mapping; a missing phone column comes back as a header-row error. */
    TargetCsvPreview previewTargets(long companyId, long campaignId, TargetImportRequest request);

    /** One sheet of an uploaded .xlsx as CSV, for the mapping step; nothing is imported. */
    SpreadsheetCsv readTargetsXlsx(long companyId, long campaignId, UploadedFile file, int sheet);

    /**
     * Empties a draft campaign's uploaded list, so it can take a new file or a contact filter instead. 404 when the
     * company has no such campaign, 409 {@code CAMPAIGN_CONTACTS_LOCKED} once it has been launched.
     *
     * @return how many targets were removed
     */
    int clearUploadedTargets(long companyId, long campaignId);

    ToolTargetPreviewResponse previewToolTargets(long companyId, ToolTargetPreviewRequest request);

    AddTargetsResponse addBatchTargets(long companyId, long campaignId, List<BatchCallTargetItemDto> targets);

    /** The campaign's target source, or null when it has none. */
    TargetSourceRow findTargetSource(long companyId, long campaignId);

    TargetSourceRow updateTargetSource(long companyId, long campaignId, UpdateTargetSourceRequest request);

    void deleteTargetSource(long companyId, long campaignId);

    /** Fetches the list now, as the recurrence sweep does on its own. */
    TargetSyncResult syncTargetsFromSource(long companyId, long campaignId);

    /**
     * The first {@code limit} (1–50) overdue Uysot debtors a {@code UYSOT_DEBTORS} campaign would
     * call today, read live — nothing is imported.
     */
    List<Debtor> previewUysotDebtors(long companyId, int limit);

    PageableData<CampaignTarget> listTargets(long companyId, long campaignId, TargetFilter filter);

    /** The target, which must belong to the campaign — 404 otherwise. */
    CampaignTarget requireTarget(long companyId, long campaignId, long targetId);

    DoNotCallResponse markDoNotCall(long companyId, long targetId);

    void applyOutcome(long companyId, long targetId, Disposition disposition);

    void scheduleCallback(long companyId, long targetId, Instant callbackAt);

    /** The company's target, or null when there is none. */
    CampaignTarget findTarget(long companyId, long targetId);

    /** The target still waiting in the campaign for this phone, or null. */
    CampaignTarget findQueuedTarget(long companyId, long campaignId, String phone);

    /** Queues the target; 0 when the phone is already waiting in the campaign. */
    long addQueuedTarget(long companyId, CampaignTarget target);

    void updateTargetStatus(long targetId, TargetStatus status, Instant nextAttemptAt);

    /** Claims up to {@code limit} of the campaign's due targets for the dialer. */
    List<CampaignTarget> claimDueTargets(long campaignId, int limit);

    /**
     * The same for a {@code SCORE} campaign: the likeliest payers first, a target whose best
     * hour is {@code localHour} (the company's) ahead of an equal one whose is not.
     */
    List<CampaignTarget> claimDueTargetsByScore(long campaignId, int limit, int localHour);

    /** The campaign's waiting targets never scored or scored before {@code scoredBefore}, never-scored first. */
    List<CampaignTarget> findTargetsToScore(long campaignId, Instant scoredBefore, int limit);

    /** Target id → its new score. */
    void updateTargetScores(long companyId, Map<Long, TargetScore> scores);

    /**
     * Claims the company campaign's next due target for operator {@code userId} — counted as
     * an attempt, as a dialer claim is. Null when nothing is due.
     */
    CampaignTarget claimNextForOperator(long companyId, long campaignId, long userId);

    /** The target operator {@code userId} holds in the company campaign and has not settled, or null. */
    CampaignTarget findHeldByOperator(long companyId, long campaignId, long userId);

    /**
     * Settles a target with what its operator said in the wrap-up: {@code DONE} or
     * {@code EXHAUSTED} closes it, {@code PENDING} dials it again — at {@code callbackAt} when
     * given, otherwise after the campaign's retry delay, unless its attempts are spent.
     */
    void settleByOperator(long companyId, long targetId, TargetStatus status, Instant callbackAt);

    /**
     * Gives a claimed target back — the attempt the claim counted included — because nothing
     * was dialled for it after all; it is due again at {@code nextAttemptAt}.
     */
    void releaseClaim(long targetId, Instant nextAttemptAt);

    /** Target id to company id, for every dialer claim whose lease ran out before {@code claimedBefore}. */
    Map<Long, Long> findCompanyIdsOfExpiredClaims(Instant claimedBefore);
}
