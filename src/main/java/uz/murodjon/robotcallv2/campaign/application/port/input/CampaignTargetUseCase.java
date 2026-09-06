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

    long addTarget(long campaignId, long clientId, String phone, String language, JsonNode contextData);

    AddTargetsResponse addTargets(long campaignId, List<AddTargetRequest> requests);

    TargetImportResult importTargetsCsv(long campaignId, String csv);

    TargetCsvPreview previewTargetsCsv(long campaignId, String csv);

    /** The campaign's target source, or null when it has none. */
    TargetSourceRow findTargetSource(long campaignId);

    TargetSourceRow updateTargetSource(long campaignId, UpdateTargetSourceRequest request);

    void deleteTargetSource(long campaignId);

    /** Fetches the list now, as the recurrence sweep does on its own. */
    TargetSyncResult syncTargetsFromSource(long campaignId);

    PageableData<CampaignTarget> listTargets(long campaignId, TargetFilter filter);

    /** The target, which must belong to the campaign — 404 otherwise. */
    CampaignTarget requireTarget(long campaignId, long targetId);

    void doNotCall(long targetId);

    DoNotCallResponse markDoNotCall(long targetId);

    void applyOutcome(long targetId, Disposition disposition);

    void scheduleCallback(long targetId, Instant callbackAt);
}
