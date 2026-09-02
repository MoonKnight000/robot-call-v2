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

    PageableData<CampaignTarget> listTargets(long campaignId, TargetFilter filter);

    void doNotCall(long targetId);

    DoNotCallResponse markDoNotCall(long targetId);

    TargetMemoryDto getTargetMemory(long targetId);

    TargetMemoryDto updateTargetMemory(long targetId, UpdateTargetMemoryRequest r);

    void applyOutcome(long targetId, Disposition disposition);

    void scheduleCallback(long targetId, Instant callbackAt);
}
