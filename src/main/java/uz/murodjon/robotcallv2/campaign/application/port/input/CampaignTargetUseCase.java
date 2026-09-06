package uz.murodjon.robotcallv2.campaign.application.port.input;

import com.fasterxml.jackson.databind.JsonNode;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallResponse;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.List;

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

    void doNotCall(long companyId, long targetId);

    DoNotCallResponse markDoNotCall(long companyId, long targetId);

    void applyOutcome(long companyId, long targetId, Disposition disposition);

    void scheduleCallback(long companyId, long targetId, Instant callbackAt);
}
