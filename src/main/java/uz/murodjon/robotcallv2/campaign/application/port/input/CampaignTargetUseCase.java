package uz.murodjon.robotcallv2.campaign.application.port.input;

import com.fasterxml.jackson.databind.JsonNode;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallResponse;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface CampaignTargetUseCase {

    long addTarget(long companyId, long campaignId, long clientId, String phone, String language,
                   JsonNode contextData);

    AddTargetsResponse addTargets(long companyId, long campaignId, List<AddTargetRequest> requests);

    TargetImportResult importTargetsCsv(long companyId, long campaignId, String csv);

    TargetCsvPreview previewTargetsCsv(long companyId, long campaignId, String csv);

    /** The campaign's target source, or null when it has none. */
    TargetSourceRow findTargetSource(long companyId, long campaignId);

    TargetSourceRow updateTargetSource(long companyId, long campaignId, UpdateTargetSourceRequest request);

    void deleteTargetSource(long companyId, long campaignId);

    /** Fetches the list now, as the recurrence sweep does on its own. */
    TargetSyncResult syncTargetsFromSource(long companyId, long campaignId);

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
     * Gives a claimed target back — the attempt the claim counted included — because nothing
     * was dialled for it after all; it is due again at {@code nextAttemptAt}.
     */
    void releaseClaim(long targetId, Instant nextAttemptAt);

    /** Target id to company id, for every dialer claim whose lease ran out before {@code claimedBefore}. */
    Map<Long, Long> findCompanyIdsOfExpiredClaims(Instant claimedBefore);
}
